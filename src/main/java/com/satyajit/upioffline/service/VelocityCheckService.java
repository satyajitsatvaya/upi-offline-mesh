package com.satyajit.upioffline.service;

/*
 * Contract for per-sender payment rate limiting.
 * Rejects if same VPA submits more than 3 payments in 10 minutes.
 */

public interface VelocityCheckService {
    boolean isAllowed(String senderVpa);
    void clear();
}