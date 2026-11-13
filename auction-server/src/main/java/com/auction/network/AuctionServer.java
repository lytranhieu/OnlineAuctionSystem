package com.auction.network;

import com.auction.service.AuctionManager;
import com.auction.service.AuctionService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AuctionServer {
    private static final int DEFAULT_PORT = 5050;
    private static final int MAX_CLIENTS = 128;
    private static final int BACKLOG = 128;

    private AuctionServer() { }

    public static void main(String[] args) throws Exception {
        String bindHost = System.getenv().getOrDefault("AUCTION_BIND", "0.0.0.0");
        int port = DEFAULT_PORT;
        Path dataFile = Path.of("data/state.json");
        int positional = 0;
        for (String arg : args) {
            if (arg.startsWith("--bind=")) bindHost = arg.substring(7);
            else if (arg.startsWith("--port=")) port = Integer.parseInt(arg.substring(7));
            else if (arg.startsWith("--data=")) dataFile = Path.of(arg.substring(7));
            else if (arg.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + arg);
            else if (positional++ == 0) port = Integer.parseInt(arg);
            else if (positional == 2) dataFile = Path.of(arg);
            else throw new IllegalArgumentException("Too many positional arguments");
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1..65535");
        if (bindHost.isBlank()) throw new IllegalArgumentException("Bind address must not be empty");

        AuctionManager manager = AuctionManager.getInstance();
        AuctionService service = manager.initialize(dataFile);
        service.bootstrapAdmin(System.getenv("AUCTION_ADMIN_PASSWORD"));
        ClientHub hub = new ClientHub();
        manager.events().subscribe(hub::broadcast);

        var scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore capacity = new Semaphore(MAX_CLIENTS);
        AtomicBoolean stopping = new AtomicBoolean(false);
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName(bindHost), port), BACKLOG);
            LanDiscoveryResponder discovery = LanDiscoveryResponder.start(bindHost, port);
            Runnable shutdown = () -> {
                if (!stopping.compareAndSet(false, true)) return;
                try { server.close(); } catch (IOException e) { System.err.println("Close listener: " + e); }
                discovery.close();
                hub.closeAll();
                scheduler.shutdownNow();
                workers.shutdown();
                try {
                    if (!workers.awaitTermination(3, TimeUnit.SECONDS)) workers.shutdownNow();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    workers.shutdownNow();
                }
                manager.events().close();
            };
            Thread shutdownHook = new Thread(shutdown, "auction-server-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            try {
                Path finalDataFile = dataFile;
                System.out.println("Auction Server listening on " + bindHost + ":" + port
                        + " | data: " + finalDataFile + " | max clients: " + MAX_CLIENTS
                        + " | LAN discovery UDP " + LanDiscoveryProtocol.UDP_PORT);
                scheduler.scheduleAtFixedRate(() -> {
                    try { service.advanceAll(); }
                    catch (RuntimeException e) { System.err.println("Auction scheduler error: " + e); }
                }, 0, 500, TimeUnit.MILLISECONDS);
                while (!stopping.get()) {
                    Socket socket;
                    try { socket = server.accept(); }
                    catch (SocketException e) {
                        if (stopping.get()) break;
                        throw e;
                    }
                    if (!capacity.tryAcquire()) {
                        System.err.println("Connection rejected: server at capacity from "
                                + socket.getRemoteSocketAddress());
                        socket.close();
                        continue;
                    }
                    try {
                        socket.setKeepAlive(true);
                        workers.execute(() -> {
                            try { new ClientHandler(socket, service, hub).run(); }
                            finally { capacity.release(); }
                        });
                    } catch (RuntimeException | IOException e) {
                        capacity.release();
                        try { socket.close(); } catch (IOException closeError) {
                            System.err.println("Close rejected socket: " + closeError);
                        }
                        if (stopping.get()) break;
                        System.err.println("Unable to accept client: " + e);
                    }
                }
            } finally {
                shutdown.run();
                try { workers.awaitTermination(3, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                manager.events().close();
                try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                catch (IllegalStateException ignored) { /* JVM shutdown already underway */ }
            }
        }
    }
}
