package com.satyajit.upioffline.model;

/*
 * Actual payment instruction inside the encrypted MeshPacket payload.
 *
 * Important security fields:
 *   - nonce: unique ID for each payment. Even identical payments generate
 *            different ciphertexts, preventing replay duplication.
 *   - signedAt: timestamp used to reject old or delayed packets.
 *   - pinHash: represents the verified UPI PIN hash in a real system.
 */

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class PaymentInstruction {

    private String senderVpa;
    private String receiverVpa;

    private BigDecimal amount;

    private String pinHash;
    private String nonce;
    private Long signedAt;
}