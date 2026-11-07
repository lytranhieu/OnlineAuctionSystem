package com.auction.model;

public final class Auction extends Entity {
    private final Item item;
    private final String seller;
    private final long startingPrice;
    private final long startsAt;
    private final long endsAt;
    private final Status status;

    public Auction(String id, Item item, String seller, long startingPrice,
                   long startsAt, long endsAt) {
        super(id);
        if (item == null) throw new IllegalArgumentException("Item is required");
        if (seller == null || seller.isBlank()) throw new IllegalArgumentException("Seller is required");
        if (startingPrice <= 0) throw new IllegalArgumentException("Starting price must be positive");
        if (startsAt >= endsAt) throw new IllegalArgumentException("End time must be later than start time");
        this.item = item;
        this.seller = seller;
        this.startingPrice = startingPrice;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = Status.OPEN;
    }

    public Item getItem() { return item; }
    public String getSeller() { return seller; }
    public long getStartingPrice() { return startingPrice; }
    public long getStartsAt() { return startsAt; }
    public long getEndsAt() { return endsAt; }
    public Status getStatus() { return status; }
}
