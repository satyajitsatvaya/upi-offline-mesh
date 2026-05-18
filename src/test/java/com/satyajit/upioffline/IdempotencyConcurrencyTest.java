package com.satyajit.upioffline;

/*
 * Concurrency and correctness tests for the core pipeline.
 * The headline test fires 3 bridge threads simultaneously against one packet
 * and asserts exactly one settles — the rest are dropped as duplicates.
 */

import com.satyajit.upioffline.crypto.HybridCryptoService;
import com.satyajit.upioffline.crypto.ServerKeyHolder;
import com.satyajit.upioffline.model.MeshPacket;
import com.satyajit.upioffline.model.PaymentInstruction;
import com.satyajit.upioffline.repository.AccountRepository;
import com.satyajit.upioffline.service.BridgeIngestionService;
import com.satyajit.upioffline.service.DemoService;
import com.satyajit.upioffline.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IdempotencyConcurrencyTest {

    @Autowired private DemoService demoService;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private IdempotencyService idempotency;
    @Autowired private AccountRepository accounts;
    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;

    @BeforeEach
    void clear() {
        idempotency.clear();
    }

    @Test
    void singlePacketDeliveredByThreeBridgesSettlesExactlyOnce() throws Exception {
        BigDecimal satyajitBefore = accounts.findById("satyajit@ybl").orElseThrow().getBalance();
        BigDecimal sounakBefore = accounts.findById("sounak@apl").orElseThrow().getBalance();

        MeshPacket packet = demoService.createPacket(
                "satyajit@ybl", "sounak@apl", new BigDecimal("100.00"), "1234", 5);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger settled = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        Future<?>[] futures = new Future[3];

        // try-with-resources ensures pool is shut down after test
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            for (int i = 0; i < 3; i++) {
                final String node = "bridge-" + i;
                futures[i] = pool.submit(() -> {
                    try {
                        start.await(); // all threads wait here until released at once
                        BridgeIngestionService.IngestResult r = bridge.ingest(packet, node, 3);
                        if ("SETTLED".equals(r.outcome())) settled.incrementAndGet();
                        else if ("DUPLICATE_DROPPED".equals(r.outcome())) duplicates.incrementAndGet();
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
            }

            start.countDown(); // release all 3 threads at the same instant
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
        }

        assertEquals(1, settled.get(), "exactly one bridge should settle");
        assertEquals(2, duplicates.get(), "the other two should be duplicates");

        BigDecimal satyajitAfter = accounts.findById("satyajit@ybl").orElseThrow().getBalance();
        BigDecimal sounakAfter = accounts.findById("sounak@apl").orElseThrow().getBalance();
        assertEquals(satyajitBefore.subtract(new BigDecimal("100.00")), satyajitAfter);
        assertEquals(sounakBefore.add(new BigDecimal("100.00")), sounakAfter);
    }

    @Test
    void tamperedCiphertextIsRejected() throws Exception {
        MeshPacket packet = demoService.createPacket(
                "satyajit@ybl", "sounak@apl", new BigDecimal("50.00"), "1234", 5);

        // flip one character — GCM tag will fail on decrypt
        char[] chars = packet.getCiphertext().toCharArray();
        chars[chars.length / 2] = chars[chars.length / 2] == 'A' ? 'B' : 'A';
        packet.setCiphertext(new String(chars));

        BridgeIngestionService.IngestResult r = bridge.ingest(packet, "bridge-x", 1);
        assertEquals("INVALID", r.outcome());
    }

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        PaymentInstruction original = new PaymentInstruction();
        original.setSenderVpa("satyajit@ybl");
        original.setReceiverVpa("sounak@apl");
        original.setAmount(new BigDecimal("123.45"));
        original.setPinHash("abcdef");
        original.setNonce("nonce-1");
        original.setSignedAt(System.currentTimeMillis());

        String ct = crypto.encrypt(original, serverKey.getPublicKey());
        PaymentInstruction decrypted = crypto.decrypt(ct);

        assertEquals(original.getSenderVpa(), decrypted.getSenderVpa());
        assertEquals(original.getReceiverVpa(), decrypted.getReceiverVpa());
        assertEquals(0, original.getAmount().compareTo(decrypted.getAmount()));
        assertEquals(original.getNonce(), decrypted.getNonce());
    }
}