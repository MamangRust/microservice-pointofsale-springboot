package com.transaction.transaction.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.transaction.transaction.entity.Outbox;
import com.transaction.transaction.entity.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByStatusOrderByCreatedAt(OutboxStatus status);
}