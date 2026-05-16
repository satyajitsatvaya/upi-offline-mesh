package com.satyajit.upioffline.service.impl;

/*
 * Orchestrates the full inbound pipeline for a packet arriving from a bridge node:
 *   1. Hash the ciphertext
 *   2. Idempotency check — drop duplicates before any work
 *   3. Decrypt
 *   4. Velocity check — reject if sender submitting too fast
 *   5. Freshness check — replay protection
 *   6. Settle
 */

import com.satyajit.upioffline.crypto.HybridCryptoService;
import com.satyajit.upioffline.model.MeshPacket;
import com.satyajit.upioffline.model.PaymentInstruction;
import com.satyajit.upioffline.model.Transaction;
import com.satyajit.upioffline.service.BridgeIngestionService;
import com.satyajit.upioffline.service.IdempotencyService;
import com.satyajit.upioffline.service.SettlementService;
import com.satyajit.upioffline.service.VelocityCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class BridgeIngestionServiceImpl implements BridgeIngestionService {

    @Autowired private HybridCryptoService crypto;
    @Autowired private IdempotencyService idempotency;
    @Autowired private VelocityCheckService velocityCheck;
    @Autowired private SettlementService settlement;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    @Override
    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {
        try {
            String packetHash = crypto.hashCiphertext(packet.getCiphertext());

            // step 1 — idempotency gate, drop duplicates before any work
            if (!idempotency.claim(packetHash)) {
                log.info("DUPLICATE {} from bridge {} — dropped",
                        packetHash.substring(0, 12) + "...", bridgeNodeId);
                return IngestResult.duplicate(packetHash);
            }

            // step 2 — decrypt to get payment details
            PaymentInstruction instruction;
            try {
                instruction = crypto.decrypt(packet.getCiphertext());
            } catch (Exception e) {
                log.warn("Decryption failed for {}: {}",
                        packetHash.substring(0, 12) + "...", e.getMessage());
                return IngestResult.invalid(packetHash, "decryption_failed");
            }

            // step 3 — velocity check
            if (!velocityCheck.isAllowed(instruction.getSenderVpa())) {
                log.warn("RATE_LIMITED sender {} — too many payments in window",
                        instruction.getSenderVpa());
                return IngestResult.rateLimited(packetHash, instruction.getSenderVpa());
            }

            // step 4 — freshness check, replay protection
            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
            if (ageSeconds > maxAgeSeconds) {
                log.warn("Packet {} too old ({}s)",
                        packetHash.substring(0, 12) + "...", ageSeconds);
                return IngestResult.invalid(packetHash, "stale_packet");
            }
            if (ageSeconds < -300) {
                return IngestResult.invalid(packetHash, "future_dated");
            }

            // step 5 — settle
            Transaction tx = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
            return IngestResult.settled(packetHash, tx);

        } catch (Exception e) {
            log.error("Ingestion error: {}", e.getMessage(), e);
            return IngestResult.invalid("?", "internal_error: " + e.getMessage());
        }
    }
}