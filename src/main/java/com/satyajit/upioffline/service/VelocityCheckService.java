package com.satyajit.upioffline.service;

/*
 * Per-sender rate limiter. Rejects if the same VPA submits more than
 * 5 payments within a 10-minute window.
 * Protects against stolen-phone abuse and accidental duplicate submissions.
 */

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VelocityCheckService {

    private static final int MAX_PAYMENTS = 5;
    private static final long WINDOW_SECONDS = 600; // 10 minutes

    // sender VPA -> list of timestamps of recent payments
    private final Map<String, List<Instant>> senderHistory = new ConcurrentHashMap<>();

    // returns true if sender is within limit, false if rate limited
    public boolean isAllowed(String senderVpa) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        senderHistory.putIfAbsent(senderVpa, new ArrayList<>());
        List<Instant> history = senderHistory.get(senderVpa);

        synchronized (history) {
            // remove timestamps outside the current window
            history.removeIf(t -> t.isBefore(windowStart));

            if (history.size() >= MAX_PAYMENTS) {
                return false; // rate limited
            }

            history.add(now);
            return true;
        }
    }

    // used by mesh reset
    public void clear() { senderHistory.clear(); }
}