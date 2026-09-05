package com.transaction.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.transaction.transaction.entity.Outbox;
import com.transaction.transaction.entity.OutboxStatus;
import com.transaction.transaction.entity.Transaction;

/**
 * One class, two repositories: both share the same Flyway schema (V1__init.sql)
 * and the same PostgreSQL container, so splitting them would just double the
 * container startup cost.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Transaction createTransaction(Long orderId, Long merchantId, Integer amount) {
        Transaction txn = new Transaction();
        txn.setOrderId(orderId);
        txn.setMerchantId(merchantId);
        txn.setAmount(amount);
        return txn;
    }

    private Outbox createOutbox(String eventId, OutboxStatus status, LocalDateTime createdAt) {
        Outbox outbox = new Outbox();
        outbox.setAggregateType("Transaction");
        outbox.setAggregateId("1");
        outbox.setTopic("stats.pos.transaction.event");
        outbox.setPayload("{\"eventType\":\"transaction.completed\"}");
        outbox.setStatus(status);
        outbox.setDomain("transaction");
        outbox.setEventId(eventId);
        outbox.setCreatedAt(createdAt);
        return outbox;
    }

    // ---- TransactionRepository ----

    @Test
    void save_persistsTransactionWithGeneratedIdAndDefaults() {
        Transaction saved = transactionRepository.save(createTransaction(10L, 1L, 111000));

        assertThat(saved.getTransactionId()).isNotNull();
        // entity-level defaults
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getChangeAmount()).isEqualTo(0);
        // @PrePersist timestamps
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void findById_returnsSavedTransaction() {
        Transaction saved = transactionRepository.save(createTransaction(10L, 1L, 111000));

        Optional<Transaction> found = transactionRepository.findById(saved.getTransactionId());

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(10L);
        assertThat(found.get().getAmount()).isEqualTo(111000);
        assertThat(found.get().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(transactionRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByOrderId_returnsOnlyThatOrdersTransactions() {
        transactionRepository.save(createTransaction(10L, 1L, 50000));
        transactionRepository.save(createTransaction(10L, 1L, 60000));
        transactionRepository.save(createTransaction(11L, 1L, 70000));

        List<Transaction> result = transactionRepository.findByOrderId(10L);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(t -> assertThat(t.getOrderId()).isEqualTo(10L));
    }

    @Test
    void findByOrderId_returnsEmptyWhenNoMatch() {
        transactionRepository.save(createTransaction(10L, 1L, 50000));

        assertThat(transactionRepository.findByOrderId(42L)).isEmpty();
    }

    @Test
    void findByIdempotencyKey_returnsMatchingTransaction() {
        Transaction txn = createTransaction(10L, 1L, 50000);
        txn.setIdempotencyKey("idem-abc");
        transactionRepository.save(txn);

        Optional<Transaction> found = transactionRepository.findByIdempotencyKey("idem-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(10L);
    }

    @Test
    void findByIdempotencyKey_returnsEmptyWhenMissing() {
        assertThat(transactionRepository.findByIdempotencyKey("nope")).isEmpty();
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Transaction saved = transactionRepository.save(createTransaction(10L, 1L, 50000));
        LocalDateTime createdAtBefore = saved.getCreatedAt();

        saved.setStatus("SUCCESS");
        Transaction updated = transactionRepository.saveAndFlush(saved);

        assertThat(updated.getStatus()).isEqualTo("SUCCESS");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Transaction saved = transactionRepository.save(createTransaction(10L, 1L, 50000));

        transactionRepository.deleteById(saved.getTransactionId());
        transactionRepository.flush();

        assertThat(transactionRepository.findById(saved.getTransactionId())).isEmpty();
    }

    @Test
    void idempotencyKey_uniqueIndex_rejectsDuplicateLiveRows() {
        Transaction first = createTransaction(10L, 1L, 50000);
        first.setIdempotencyKey("idem-dup");
        transactionRepository.saveAndFlush(first);

        Transaction second = createTransaction(10L, 1L, 60000);
        second.setIdempotencyKey("idem-dup");

        assertThatThrownBy(() -> {
            transactionRepository.save(second);
            transactionRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKey_softDeletedRowStillBlocksReuse() {
        Transaction deleted = createTransaction(10L, 1L, 50000);
        deleted.setIdempotencyKey("idem-soft");
        deleted.setDeletedAt(LocalDateTime.now());
        transactionRepository.saveAndFlush(deleted);

        Transaction replacement = createTransaction(10L, 1L, 60000);
        replacement.setIdempotencyKey("idem-soft");

        // Product quirk (documented, NOT fixed here): V1__init.sql defines BOTH
        // a full UNIQUE constraint on idempotency_key (transactions_idempotency_key_key)
        // AND the partial index (WHERE deleted_at IS NULL). The full constraint wins,
        // so the partial index's "reuse after soft delete" semantics never take effect.
        assertThatThrownBy(() -> {
            transactionRepository.save(replacement);
            transactionRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- OutboxRepository ----

    @Test
    void save_persistsOutboxWithGeneratedIdAndDefaults() {
        Outbox saved = outboxRepository.save(createOutbox("evt-1", OutboxStatus.PENDING, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isEqualTo(0);
        // @PrePersist fills created_at when not set explicitly
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getProcessedAt()).isNull();
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    void findByStatusOrderByCreatedAt_returnsOnlyPendingSortedAscending() {
        // @PrePersist overwrites createdAt on insert, so distinct timestamps
        // must be applied via an UPDATE after the rows are persisted.
        Outbox p1 = outboxRepository.save(createOutbox("evt-pending-1", OutboxStatus.PENDING, null));
        Outbox p2 = outboxRepository.save(createOutbox("evt-pending-2", OutboxStatus.PENDING, null));
        Outbox p3 = outboxRepository.save(createOutbox("evt-pending-3", OutboxStatus.PENDING, null));
        outboxRepository.save(createOutbox("evt-processed", OutboxStatus.PROCESSED, null));

        LocalDateTime base = LocalDateTime.of(2026, 9, 4, 10, 0);
        p1.setCreatedAt(base);
        p2.setCreatedAt(base.plusMinutes(5));
        p3.setCreatedAt(base.minusMinutes(5));
        outboxRepository.saveAllAndFlush(List.of(p1, p2, p3));

        List<Outbox> result = outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Outbox::getEventId)
                .containsExactly("evt-pending-3", "evt-pending-1", "evt-pending-2");
    }

    @Test
    void findByStatusOrderByCreatedAt_returnsEmptyWhenNoPending() {
        outboxRepository.save(createOutbox("evt-processed-2", OutboxStatus.PROCESSED, null));

        assertThat(outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING)).isEmpty();
    }
}
