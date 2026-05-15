package com.satyajit.upioffline.model;

/*
 * Permanent record of every settled payment.
 * The packetHash is the idempotency key — uniqueness is enforced at the DB level
 * as a defense-in-depth fallback if the Redis-style cache layer ever fails.
 */

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "transactions",
        indexes = { @Index(name = "idx_packet_hash", columnList = "packetHash", unique = true) })
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String packetHash; // SHA-256 hex of the ciphertext

    @Column(nullable = false)
    private String senderVpa;

    @Column(nullable = false)
    private String receiverVpa;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant signedAt; // when sender signed it offline

    @Column(nullable = false)
    private Instant settledAt; // when backend processed it

    @Column(nullable = false)
    private String bridgeNodeId; // which mesh node delivered it

    @Column(nullable = false)
    private int hopCount; // how many devices it passed through

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status { SETTLED, REJECTED }
}