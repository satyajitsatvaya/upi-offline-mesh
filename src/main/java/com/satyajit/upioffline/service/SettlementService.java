package com.satyajit.upioffline.service;

/*
 * Contract for ledger settlement.
 * Debits sender, credits receiver, writes transaction record.
 */

import com.satyajit.upioffline.model.PaymentInstruction;
import com.satyajit.upioffline.model.Transaction;

public interface SettlementService {
    Transaction settle(PaymentInstruction instruction, String packetHash,
                       String bridgeNodeId, int hopCount);
}