package com.auction.service;

import com.auction.dto.AuctionView;
import com.auction.model.Auction;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Xử lý đặt giá và báo cho các bên đăng ký biết khi có bid mới (Observer đơn giản). */
public final class BidService {
    private final AuctionManager manager;
    private final List<Consumer<AuctionView>> listeners = new CopyOnWriteArrayList<>();

    public BidService(AuctionManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    /** Đăng ký nhận thông báo mỗi khi có bid hợp lệ. */
    public void addListener(Consumer<AuctionView> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public AuctionView placeBid(String auctionId, String bidder, long amount, long now) {
        Objects.requireNonNull(bidder, "bidder");
        Auction auction = manager.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("Auction not found: " + auctionId));
        auction.bid(bidder, amount, now, false); // khóa nằm bên trong Auction.bid()
        AuctionView view = auction.view();       // lấy ảnh chụp sau khi đã nhả khóa
        listeners.forEach(listener -> listener.accept(view));
        return view;
    }
}