package com.satyajit.upioffline.service;

/*
 * Contract for bridge node packet ingestion pipeline.
 * Orchestrates: hash → idempotency → decrypt → velocity → freshness → settle.
 */

import com.satyajit.upioffline.model.MeshPacket;

public interface BridgeIngestionService {
    IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount);

    record IngestResult(String outcome, String packetHash, String reason, Long transactionId) {
        public static IngestResult settled(String hash, com.satyajit.upioffline.model.Transaction tx) {
            return new IngestResult("SETTLED", hash, null, tx.getId());
        }
        public static IngestResult duplicate(String hash) {
            return new IngestResult("DUPLICATE_DROPPED", hash, null, null);
        }
        public static IngestResult invalid(String hash, String reason) {
            return new IngestResult("INVALID", hash, reason, null);
        }
        public static IngestResult rateLimited(String hash, String senderVpa) {
            return new IngestResult("RATE_LIMITED", hash, "velocity_limit_exceeded:" + senderVpa, null);
        }
    }
}