package com.auction.model;

public record BidTransaction(String bidder, long amount, long at, boolean automatic) { }
