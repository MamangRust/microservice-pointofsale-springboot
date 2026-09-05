package com.transaction.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.transaction.transaction.entity.Outbox;
import com.transaction.transaction.entity.OutboxStatus;
import com.transaction.transaction.entity.Transaction;
import com.transaction.transaction.repository.OutboxRepository;
import com.transaction.transaction.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private TransactionService transactionService;

    // Real mapper (not a mock): writeOutbox serializes the EventEnvelope through it.
    // JavaTimeModule mirrors Spring Boot's auto-configured ObjectMapper in production —
    // EventEnvelope.occurredAt is an Instant which a bare ObjectMapper would fail on.
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, outboxRepository, objectMapper);
    }

    private Transaction createTransaction(Long orderId, Long merchantId, Integer amount, String paymentMethod) {
        Transaction txn = new Transaction();
        txn.setOrderId(orderId);
        txn.setMerchantId(merchantId);
        txn.setAmount(amount);
        txn.setPaymentMethod(paymentMethod);
        txn.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0, 30));
        return txn;
    }

    private void stubSaveAssignsId(long id) {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction txn = inv.getArgument(0);
            txn.setTransactionId(id);
            return txn;
        });
    }

    // ---- calculateTotalWithTax (static, server-side total incl. 11% PPN) ----

    @Test
    void calculateTotalWithTax_shouldInclude11PercentPpn() {
        // items total = 100000, expected = 100000 * 1.11 = 111000
        assertThat(TransactionService.calculateTotalWithTax(100000)).isEqualTo(111000);
    }

    @Test
    void calculateTotalWithTax_shouldRoundCorrectly() {
        // Math.round(166.5) = 167 (half-up)
        assertThat(TransactionService.calculateTotalWithTax(150)).isEqualTo(167);
        assertThat(TransactionService.calculateTotalWithTax(100)).isEqualTo(111);
        // Math.round(165.39) = 165
        assertThat(TransactionService.calculateTotalWithTax(149)).isEqualTo(165);
    }

    @Test
    void calculateTotalWithTax_shouldHandleZero() {
        assertThat(TransactionService.calculateTotalWithTax(0)).isZero();
    }

    // ---- create: happy path + outbox write ----

    @Test
    void create_savesTransactionAndWritesOutboxEvent() throws Exception {
        Transaction input = createTransaction(10L, 1L, 111000, "QRIS");
        stubSaveAssignsId(7L);

        Transaction result = transactionService.create(input);

        assertThat(result.getTransactionId()).isEqualTo(7L);
        verify(transactionRepository).save(input);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox outbox = outboxCaptor.getValue();

        assertThat(outbox.getAggregateType()).isEqualTo("Transaction");
        assertThat(outbox.getAggregateId()).isEqualTo("7");
        assertThat(outbox.getTopic()).isEqualTo("stats.pos.transaction.event");
        assertThat(outbox.getDomain()).isEqualTo("transaction");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventId()).isNotBlank();

        JsonNode envelope = objectMapper.readTree(outbox.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("transaction.completed");
        assertThat(envelope.get("domain").asText()).isEqualTo("transaction");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isEqualTo(outbox.getEventId());

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("eventId").asText()).isNotBlank();
        assertThat(payload.get("transactionId").asLong()).isEqualTo(7L);
        assertThat(payload.get("orderId").asLong()).isEqualTo(10L);
        assertThat(payload.get("merchantId").asLong()).isEqualTo(1L);
        assertThat(payload.get("paymentMethod").asText()).isEqualTo("QRIS");
        assertThat(payload.get("status").asText()).isEqualTo("PENDING");
        assertThat(payload.get("amount").asInt()).isEqualTo(111000);
        assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-09-04T10:00:30");
    }

    @Test
    void create_withNullPaymentMethod_defaultsPayloadToCASH() throws Exception {
        Transaction input = createTransaction(10L, 1L, 25000, null);
        stubSaveAssignsId(9L);

        transactionService.create(input);

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload()).get("payload");
        assertThat(payload.get("paymentMethod").asText()).isEqualTo("CASH");
    }

    @Test
    void create_withNullCreatedAt_writesEmptyOccurredAt() throws Exception {
        Transaction input = createTransaction(10L, 1L, 25000, "CASH");
        input.setCreatedAt(null);
        stubSaveAssignsId(11L);

        transactionService.create(input);

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload()).get("payload");
        assertThat(payload.get("occurredAt").asText()).isEmpty();
    }

    // ---- create: idempotency guard ----

    @Test
    void create_withDuplicateIdempotencyKey_throwsAndSkipsSave() {
        Transaction input = createTransaction(10L, 1L, 50000, "CASH");
        input.setIdempotencyKey("idem-1");
        when(transactionRepository.findByIdempotencyKey("idem-1"))
                .thenReturn(Optional.of(new Transaction()));

        assertThatThrownBy(() -> transactionService.create(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Duplicate transaction: idempotency key already processed");

        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    @Test
    void create_withoutIdempotencyKey_skipsDuplicateLookupAndSaves() {
        Transaction input = createTransaction(10L, 1L, 50000, "CASH");
        stubSaveAssignsId(12L);

        Transaction result = transactionService.create(input);

        assertThat(result.getTransactionId()).isEqualTo(12L);
        // Strict mocks double as an assertion: findByIdempotencyKey must never be consulted.
        verify(transactionRepository).save(input);
        verify(outboxRepository).save(any(Outbox.class));
    }

    // ---- create: outbox failure must not fail the transaction ----

    @Test
    void create_withOutboxSaveFailure_stillPersistsTransaction() {
        Transaction input = createTransaction(10L, 1L, 50000, "CASH");
        stubSaveAssignsId(8L);
        when(outboxRepository.save(any(Outbox.class))).thenThrow(new RuntimeException("kafka down"));

        Transaction result = transactionService.create(input);

        assertThat(result.getTransactionId()).isEqualTo(8L);
        verify(transactionRepository).save(input);
    }

    @Test
    void create_withOutboxSerializationFailure_stillPersistsTransaction() {
        // Bare ObjectMapper lacks JavaTimeModule -> EventEnvelope(Instant) serialization fails.
        // The service swallows it; the transaction itself must still be persisted.
        TransactionService bareMapperService = new TransactionService(
                transactionRepository, outboxRepository, new ObjectMapper());
        Transaction input = createTransaction(10L, 1L, 50000, "CASH");
        stubSaveAssignsId(13L);

        Transaction result = bareMapperService.create(input);

        assertThat(result.getTransactionId()).isEqualTo(13L);
        verify(transactionRepository).save(input);
        verify(outboxRepository, never()).save(any(Outbox.class));
    }

    // ---- complete / fail: status transitions ----

    @Test
    void complete_setsStatusSuccessAndSaves() {
        Transaction existing = createTransaction(10L, 1L, 50000, "CASH");
        existing.setTransactionId(1L);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));

        transactionService.complete(1L);

        assertThat(existing.getStatus()).isEqualTo("SUCCESS");
        verify(transactionRepository).save(existing);
    }

    @Test
    void fail_setsStatusFailedAndSaves() {
        Transaction existing = createTransaction(10L, 1L, 50000, "CASH");
        existing.setTransactionId(2L);
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(existing));

        transactionService.fail(2L);

        assertThat(existing.getStatus()).isEqualTo("FAILED");
        verify(transactionRepository).save(existing);
    }

    @Test
    void complete_throwsWhenTransactionMissing() {
        when(transactionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.complete(404L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Transaction not found");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ---- read passthroughs ----

    @Test
    void getById_returnsTransactionWhenFound() {
        Transaction existing = createTransaction(10L, 1L, 50000, "CASH");
        existing.setTransactionId(3L);
        when(transactionRepository.findById(3L)).thenReturn(Optional.of(existing));

        assertThat(transactionService.getById(3L)).isSameAs(existing);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Transaction not found");
    }

    @Test
    void getAll_returnsAllFromRepository() {
        Transaction t1 = createTransaction(10L, 1L, 50000, "CASH");
        Transaction t2 = createTransaction(11L, 1L, 75000, "QRIS");
        when(transactionRepository.findAll()).thenReturn(List.of(t1, t2));

        assertThat(transactionService.getAll()).containsExactly(t1, t2);
        verify(transactionRepository).findAll();
    }

    @Test
    void getByOrderId_returnsFromRepository() {
        Transaction t1 = createTransaction(10L, 1L, 50000, "CASH");
        when(transactionRepository.findByOrderId(10L)).thenReturn(List.of(t1));

        assertThat(transactionService.getByOrderId(10L)).containsExactly(t1);
        verify(transactionRepository).findByOrderId(10L);
    }

    @Test
    void getByOrderId_returnsEmptyWhenNoMatch() {
        when(transactionRepository.findByOrderId(42L)).thenReturn(List.of());

        assertThat(transactionService.getByOrderId(42L)).isEmpty();
    }
}
