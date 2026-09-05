package com.transaction.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.transaction.transaction.entity.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByOrderId(Long orderId);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}