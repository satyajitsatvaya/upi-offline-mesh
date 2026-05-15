package com.satyajit.upioffline.model;

/*
 * Data format shared between devices over Bluetooth.
 *
 * Intermediate devices can read outer fields like packetId, ttl, and createdAt
 * for routing and deduplication. The ciphertext itself stays encrypted and
 * unreadable without the server's private key.
 *
 * Outer fields can still be modified by a malicious relay device. To avoid
 * replay issues, the server uses the ciphertext hash (not packetId) as the
 * idempotency key. Any change inside the encrypted payload is detected during
 * decryption.
 */

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MeshPacket {

    // UUID assigned by the sender's device — used by intermediates for gossip dedup
    @NotBlank
    private String packetId;

    // Hops remaining — intermediates decrement this before forwarding
    // packet is dropped when it reaches zero
    @Min(0)
    private int ttl;

    // Epoch millis — when the sender created this packet
    @NotNull
    private Long createdAt;

    // base64(RSA-encrypted AES key + IV + AES-GCM ciphertext)
    // opaque to all intermediaries — tampering breaks the GCM auth tag
    @NotBlank
    private String ciphertext;
}