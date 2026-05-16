package com.satyajit.upioffline.service.impl;

/*
 * Seeds demo accounts on startup and simulates the sender phone
 * creating an encrypted payment packet before injecting into the mesh.
 * In production, createPacket() runs on the Android device in Kotlin.
 */

import com.satyajit.upioffline.crypto.HybridCryptoService;
import com.satyajit.upioffline.crypto.ServerKeyHolder;
import com.satyajit.upioffline.model.Account;
import com.satyajit.upioffline.model.MeshPacket;
import com.satyajit.upioffline.model.PaymentInstruction;
import com.satyajit.upioffline.repository.AccountRepository;
import com.satyajit.upioffline.service.DemoService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class DemoServiceImpl implements DemoService {

    @Autowired private AccountRepository accounts;
    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;

    @PostConstruct
    public void seedAccounts() {
        if (accounts.count() == 0) {
            accounts.save(new Account("alice@demo", "Alice", new BigDecimal("5000.00"), null));
            accounts.save(new Account("bob@demo",   "Bob",   new BigDecimal("1000.00"), null));
            accounts.save(new Account("carol@demo", "Carol", new BigDecimal("2500.00"), null));
            accounts.save(new Account("dave@demo",  "Dave",  new BigDecimal("500.00"),  null));
            log.info("Seeded 4 demo accounts");
        }
    }

    @Override
    public MeshPacket createPacket(String senderVpa, String receiverVpa,
                                   BigDecimal amount, String pin, int ttl) throws Exception {
        PaymentInstruction instruction = new PaymentInstruction();
        instruction.setSenderVpa(senderVpa);
        instruction.setReceiverVpa(receiverVpa);
        instruction.setAmount(amount);
        instruction.setPinHash(sha256Hex(pin));
        instruction.setNonce(UUID.randomUUID().toString());
        instruction.setSignedAt(Instant.now().toEpochMilli());

        String ciphertext = crypto.encrypt(instruction, serverKey.getPublicKey());

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCiphertext(ciphertext);
        return packet;
    }

    // raw PIN never travels anywhere — only its SHA-256 hash enters the payload
    private String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}