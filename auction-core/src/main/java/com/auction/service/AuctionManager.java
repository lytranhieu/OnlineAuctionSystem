package com.auction.service;

import com.auction.model.Auction;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Singleton: stores demo auction objects in memory for the initial project skeleton. */
public final class AuctionManager {
    private static final AuctionManager INSTANCE = new AuctionManager();
    private final Map<String, Auction> auctions = new LinkedHashMap<>();

    private AuctionManager() { }

    public static AuctionManager getInstance() { return INSTANCE; }

    public void register(Auction auction) {
        Objects.requireNonNull(auction, "auction");
        if (auctions.containsKey(auction.getId())) {
            throw new IllegalArgumentException("Duplicate auction ID: " + auction.getId());
        }
        auctions.put(auction.getId(), auction);
    }

    public Optional<Auction> findById(String id) {
        return Optional.ofNullable(auctions.get(id));
    }

    public Collection<Auction> findAll() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(auctions.values()));
    }
}
