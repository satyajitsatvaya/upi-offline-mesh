package com.satyajit.upioffline.service.impl;

/*
 * Handles the actual ledger update — debit sender, credit receiver, write transaction record.
 * Wrapped in @Transactional so either everything commits or nothing does.
 * @Version on Account gives optimistic locking as last-resort defense against concurrent updates.
 */

import com.satyajit.upioffline.model.Account;
import com.satyajit.upioffline.model.PaymentInstruction;
import com.satyajit.upioffline.model.Transaction;
import com.satyajit.upioffline.repository.AccountRepository;
import com.satyajit.upioffline.repository.TransactionRepository;
import com.satyajit.upioffline.service.SettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
public class SettlementServiceImpl implements SettlementService {

    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;

    @Override
    @Transactional
    public Transaction settle(PaymentInstruction instruction, String packetHash,
                              String bridgeNodeId, int hopCount) {

        Account sender = accounts.findById(instruction.getSenderVpa())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown sender VPA: " + instruction.getSenderVpa()));

        Account receiver = accounts.findById(instruction.getReceiverVpa())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown receiver VPA: " + instruction.getReceiverVpa()));

        BigDecimal amount = instruction.getAmount();
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: {} has ₹{}, tried to send ₹{}",
                    sender.getVpa(), sender.getBalance(), amount);
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount);
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(amount);
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.SETTLED);
        transactions.save(tx);

        log.info("SETTLED ₹{} from {} to {} (hash={}, bridge={}, hops={})",
                amount, sender.getVpa(), receiver.getVpa(),
                packetHash.substring(0, 12) + "...", bridgeNodeId, hopCount);

        return tx;
    }

    private Transaction recordRejected(PaymentInstruction instruction, String packetHash,
                                       String bridgeNodeId, int hopCount) {
        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(instruction.getAmount());
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.REJECTED);
        return transactions.save(tx);
    }
}