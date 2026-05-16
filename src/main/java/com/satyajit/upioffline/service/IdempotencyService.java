package com.satyajit.upioffline.service;

/*
 * In-memory idempotency cache. Kills the duplicate-storm problem —
 * even if 100 bridge nodes deliver the same packet simultaneously,
 * exactly one settles. The rest are dropped here before any DB work.
 *
 * In production this becomes Redis: SET key NX EX 86400 — same semantics,
 * distributed across server instances.
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    @Value("${upi.mesh.idempotency-ttl-seconds:86400}")
    private long ttlSeconds;

    // returns true if first claimer, false if duplicate
    // putIfAbsent is atomic — thread-safe without any locking
    public boolean claim(String packetHash) {
        Instant now = Instant.now();
        Instant prev = seen.putIfAbsent(packetHash, now);
        return prev == null;
    }

    public int size() { return seen.size(); }

    // runs every 60 seconds — removes entries older than TTL
    // prevents the map from growing indefinitely
    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
        seen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    // used by mesh reset and tests to wipe the cache
    public void clear() { seen.clear(); }
}