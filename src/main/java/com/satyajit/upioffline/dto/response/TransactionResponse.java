package com.satyajit.upioffline.dto.response;

/*
 * API response shape for settled or rejected transactions.
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String status;
    private Instant signedAt;
    private Instant settledAt;
    private String bridgeNodeId;
    private int hopCount;
}