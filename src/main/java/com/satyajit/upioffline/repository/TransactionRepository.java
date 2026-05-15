package com.satyajit.upioffline.repository;

import com.satyajit.upioffline.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/*
 * Data access layer for settled transactions.
 * existsByPacketHash is the DB-level idempotency fallback —
 * catches duplicates if the in-memory cache is wiped on restart.
 */

public interface TransactionRepository extends JpaRepository<Transaction,String> {

    List<Transaction> findTop20ByOrderByIdDesc();
    boolean existsByPacketHash(String packetHash);
}
