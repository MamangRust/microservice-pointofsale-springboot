package com.order.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.order.entity.Outbox;
import com.order.order.entity.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findByStatusOrderByCreatedAt(OutboxStatus status);
}
