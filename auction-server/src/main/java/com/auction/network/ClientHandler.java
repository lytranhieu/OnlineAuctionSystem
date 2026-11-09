package com.auction.network;

import com.auction.model.*;
import com.auction.dto.AuctionView;
import com.auction.service.AuctionService;
import com.google.gson.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class ClientHandler implements Runnable {
    private static final int MAX_LINE_LENGTH = 64_000;
    private final Socket socket;
    private final AuctionService service;
    private final ClientHub hub;
    private final Gson gson = new Gson();
    private final BlockingQueue<Packet> outgoing = new ArrayBlockingQueue<>(256);
    private PrintWriter output;
    private volatile User session;
    private final Set<String> knownAuctions = ConcurrentHashMap.newKeySet();

    public ClientHandler(Socket socket, AuctionService service, ClientHub hub) {
        this.socket = socket; this.service = service; this.hub = hub;
    }

    @Override public void run() {
        Thread sender = null;
        try (socket;
             var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            output = writer;
            sender = Thread.ofVirtual().start(this::writeLoop);
            hub.add(this);
            String line;
            while ((line = JsonLineFraming.readLine(input, MAX_LINE_LENGTH)) != null) {
                Packet request = null;
                try {
                    request = gson.fromJson(line, Packet.class);
                    if (request == null || request.type == null || request.id == null)
                        throw new IllegalArgumentException("Invalid request");
                    send(Packet.result(request.id, handle(request), gson));
                } catch (RuntimeException e) {
                    System.err.println("Rejected " + (request == null ? "malformed JSON" : request.type)
                            + " from " + socket.getRemoteSocketAddress() + ": "
                            + e.getClass().getSimpleName());
                    String reason = e instanceof JsonParseException ? "Malformed JSON request" : e.getMessage();
                    send(Packet.error(request == null ? "" : request.id,
                            reason == null ? "Invalid request" : reason));
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) System.err.println("Client " + socket.getRemoteSocketAddress()
                    + " disconnected: " + e);
        } finally {
            hub.remove(this);
            if (sender != null) sender.interrupt();
        }
    }

    public boolean authenticated() {
        if (session == null) return false;
        try { service.requireActive(session); return true; }
        catch (SecurityException ignored) { return false; }
    }

    public boolean canSee(AuctionView auction) {
        if (!authenticated()) return false;
        boolean privateSubmission = auction.status() == Status.PENDING
                || auction.status() == Status.REJECTED;
        return !privateSubmission || session.getRole() == Role.ADMIN
                || session.getUsername().equals(auction.seller());
    }

    public void remember(List<AuctionView> auctions) {
        auctions.forEach(a -> knownAuctions.add(a.id()));
    }

    public boolean removeKnown(String id) {
        return knownAuctions.remove(id);
    }

    public void remember(AuctionView auction) {
        knownAuctions.add(auction.id());
    }

    public void send(Packet packet) {
        if (!outgoing.offer(packet)) disconnect();
    }

    private void writeLoop() {
        try {
            while (!socket.isClosed()) {
                output.println(gson.toJson(outgoing.take()));
                if (output.checkError()) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally { disconnect(); }
    }

    public void disconnect() {
        hub.remove(this);
        knownAuctions.clear();
        session = null;
        try { socket.close(); }
        catch (IOException e) { System.err.println("Close client socket: " + e); }
    }

    private Object handle(Packet request) {
        JsonObject data = request.data != null && request.data.isJsonObject()
                ? request.data.getAsJsonObject() : new JsonObject();
        return switch (request.type) {
            case "REGISTER" -> {
                User user = service.register(str(data, "username"), str(data, "password"),
                        Role.valueOf(str(data, "role")));
                yield Map.of("username", user.getUsername(), "role", user.getRole());
            }
            case "LOGIN" -> {
                session = service.login(str(data, "username"), str(data, "password"));
                knownAuctions.clear();
                yield Map.of("username", session.getUsername(), "role", session.getRole());
            }
            case "LOGOUT" -> {
                session = null;
                knownAuctions.clear();
                yield Map.of("message", "Logged out");
            }
            case "USERS" -> service.listUsers(authorized());
            case "SUSPEND_USER" -> service.setUserSuspended(authorized(), str(data, "username"), true);
            case "RESTORE_USER" -> service.setUserSuspended(authorized(), str(data, "username"), false);
            case "LIST" -> {
                List<AuctionView> views = service.list(authorized());
                remember(views);
                yield views;
            }
            case "DETAIL" -> {
                AuctionView view = service.get(authorized(), str(data, "id"));
                remember(view);
                yield view;
            }
            case "CREATE" -> service.create(authorized(), str(data, "name"), str(data, "description"),
                    Category.valueOf(str(data, "category")), num(data, "price"),
                    num(data, "startsAt"), num(data, "endsAt"), num(data, "minimumIncrement"));
            case "EDIT" -> service.edit(authorized(), str(data, "id"), str(data, "name"),
                    str(data, "description"), Category.valueOf(str(data, "category")),
                    num(data, "price"), num(data, "startsAt"), num(data, "endsAt"), num(data, "minimumIncrement"));
            case "DELETE" -> {
                String id = str(data, "id");
                service.delete(authorized(), id);
                hub.removed(id);
                yield Map.of("deleted", id);
            }
            case "BID" -> service.bid(authorized(), str(data, "id"), num(data, "amount"));
            case "AUTO_BID" -> {
                User bidder = authorized();
                String id = str(data, "id");
                long increment = data.has("increment") ? num(data, "increment")
                        : service.get(bidder, id).minimumIncrement();
                yield service.registerAuto(bidder, id, num(data, "maxBid"), increment);
            }
            case "APPROVE" -> service.approve(authorized(), str(data, "id"));
            case "REJECT" -> service.reject(authorized(), str(data, "id"), str(data, "reason"));
            case "CANCEL" -> service.cancel(authorized(), str(data, "id"));
            case "PAY" -> service.pay(authorized(), str(data, "id"));
            default -> throw new IllegalArgumentException("Unknown command: " + request.type);
        };
    }

    private User authorized() {
        if (session == null) throw new SecurityException("Please login first");
        service.requireActive(session);
        return session;
    }

    private static String str(JsonObject data, String key) {
        if (!data.has(key) || data.get(key).isJsonNull())
            throw new IllegalArgumentException("Missing " + key);
        String value = data.get(key).getAsString();
        if (value.length() > 2000) throw new IllegalArgumentException("Field too long: " + key);
        return value;
    }

    private static long num(JsonObject data, String key) {
        try { return Long.parseLong(str(data, key)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid integer: " + key); }
    }
}
