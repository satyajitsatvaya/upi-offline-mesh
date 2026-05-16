package com.satyajit.upioffline.service.impl;

/*
 * Per-sender rate limiter. Rejects if the same VPA submits more than
 * 3 payments within a 10-minute window.
 * Protects against stolen-phone abuse and accidental duplicate submissions.
 */

import com.satyajit.upioffline.service.VelocityCheckService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VelocityCheckServiceImpl implements VelocityCheckService {

    private static final int MAX_PAYMENTS = 3;
    private static final long WINDOW_SECONDS = 600;

    private final Map<String, List<Instant>> senderHistory = new ConcurrentHashMap<>();

    @Override
    public boolean isAllowed(String senderVpa) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        senderHistory.putIfAbsent(senderVpa, new ArrayList<>());
        List<Instant> history = senderHistory.get(senderVpa);

        synchronized (history) {
            history.removeIf(t -> t.isBefore(windowStart));
            if (history.size() >= MAX_PAYMENTS) {
                return false;
            }
            history.add(now);
            return true;
        }
    }

    @Override
    public void clear() { senderHistory.clear(); }
}