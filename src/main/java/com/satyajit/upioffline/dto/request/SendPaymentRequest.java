package com.satyajit.upioffline.dto.request;

/*
 * Request body for POST /api/demo/send
 * Simulates the sender phone submitting a payment into the mesh.
 */

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class SendPaymentRequest {

    @NotBlank
    private String senderVpa;

    @NotBlank
    private String receiverVpa;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank
    private String pin;

    // defaults to 5 if not provided
    private Integer ttl;

    // defaults to phone-alice if not provided
    private String startDevice;
}