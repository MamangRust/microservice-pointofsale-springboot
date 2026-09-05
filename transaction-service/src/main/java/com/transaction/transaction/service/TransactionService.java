package com.transaction.transaction.service;

import com.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transaction.transaction.entity.Outbox;
import com.transaction.transaction.entity.OutboxStatus;
import com.transaction.transaction.entity.Transaction;
import com.transaction.transaction.repository.OutboxRepository;
import com.transaction.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final double TAX_RATE = 0.11; // 11% PPN

    private final TransactionRepository repository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TransactionService(TransactionRepository repository,
                              OutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Server-side total: SUM(price * qty) + 11% PPN. Client amount is NOT trusted (OT-2).
     */
    public static int calculateTotalWithTax(long itemsTotal) {
        return (int) Math.round(itemsTotal * (1 + TAX_RATE));
    }

    public List<Transaction> getAll() { return repository.findAll(); }
    public List<Transaction> getByOrderId(Long orderId) { return repository.findByOrderId(orderId); }
    public Transaction getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Transaction not found"));
    }

    @Transactional
    public Transaction create(Transaction txn) {
        // Idempotency guard
        if (txn.getIdempotencyKey() != null
                && repository.findByIdempotencyKey(txn.getIdempotencyKey()).isPresent()) {
            throw new RuntimeException("Duplicate transaction: idempotency key already processed");
        }
        Transaction saved = repository.save(txn);
        // Publish transaction.completed event via outbox
        writeOutbox(saved);
        return saved;
    }

    @Transactional
    public void complete(Long id) {
        Transaction txn = getById(id);
        txn.setStatus("SUCCESS");
        repository.save(txn);
    }

    @Transactional
    public void fail(Long id) {
        Transaction txn = getById(id);
        txn.setStatus("FAILED");
        repository.save(txn);
    }

    private void writeOutbox(Transaction txn) {
        try {
            Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "transactionId", txn.getTransactionId(),
                "orderId", txn.getOrderId(),
                "merchantId", txn.getMerchantId(),
                "paymentMethod", txn.getPaymentMethod() == null ? "CASH" : txn.getPaymentMethod(),
                "status", txn.getStatus(),
                "amount", txn.getAmount(),
                "occurredAt", txn.getCreatedAt() == null ? "" : txn.getCreatedAt().toString()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "transaction.completed", "transaction");

            Outbox outbox = new Outbox();
            outbox.setAggregateType("Transaction");
            outbox.setAggregateId(String.valueOf(txn.getTransactionId()));
            outbox.setTopic("stats.pos.transaction.event");
            outbox.setPayload(objectMapper.writeValueAsString(envelope));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setDomain("transaction");
            outbox.setEventId(envelope.eventId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write transaction outbox: {}", e.getMessage());
        }
    }
}