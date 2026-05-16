package com.satyajit.upioffline.dto.response;

/*
 * API response shape for account data.
 * Strips internal JPA fields like version from the response.
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AccountResponse {
    private String vpa;
    private String holderName;
    private BigDecimal balance;
}