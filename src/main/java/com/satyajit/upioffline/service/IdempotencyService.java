package com.satyajit.upioffline.service;

/*
 * Contract for the idempotency cache.
 * Current impl: in-memory ConcurrentHashMap.
 * Production swap: Redis SET NX EX — same interface, different impl.
 */

public interface IdempotencyService {
    boolean claim(String packetHash);
    int size();
    void clear();
}