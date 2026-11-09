package com.auction.model;

public record AutoBid(String bidder, long maxBid, long increment, long createdAt) { }
