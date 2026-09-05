package com.statswriter.statswriter.consumer;

import com.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Kafka consumer for stats events — mirrors Quarkus StatsWriterConsumer.
 * Batches events and flushes to ClickHouse every 5s or 1000 rows.
 */
@Component
public class StatsWriterConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatsWriterConsumer.class);
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate clickhouseJdbc;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedQueue<Map<String, Object>> orderBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> transactionBuffer = new ConcurrentLinkedQueue<>();

    public StatsWriterConsumer(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbc,
                                ObjectMapper objectMapper) {
        this.clickhouseJdbc = clickhouseJdbc;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "stats.pos.order.event", groupId = "stats-writer")
    public void consumeOrderEvent(String message) {
        try {
            EventEnvelope<Map<String, Object>> envelope = objectMapper.readValue(
                message, new TypeReference<EventEnvelope<Map<String, Object>>>() {});
            orderBuffer.offer(envelope.payload());
            if (orderBuffer.size() >= BATCH_SIZE) {
                flushOrderBuffer();
            }
        } catch (Exception e) {
            log.error("Failed to consume order event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "stats.pos.transaction.event", groupId = "stats-writer")
    public void consumeTransactionEvent(String message) {
        try {
            EventEnvelope<Map<String, Object>> envelope = objectMapper.readValue(
                message, new TypeReference<EventEnvelope<Map<String, Object>>>() {});
            transactionBuffer.offer(envelope.payload());
            if (transactionBuffer.size() >= BATCH_SIZE) {
                flushTransactionBuffer();
            }
        } catch (Exception e) {
            log.error("Failed to consume transaction event: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledFlush() {
        flushOrderBuffer();
        flushTransactionBuffer();
    }

    @Transactional
    public void flushOrderBuffer() {
        if (orderBuffer.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> item;
        while ((item = orderBuffer.poll()) != null) {
            batch.add(item);
        }
        try {
            for (Map<String, Object> row : batch) {
                clickhouseJdbc.update(
                    "INSERT INTO pos_stats.order_daily (event_id, event_version, occurred_at, " +
                    "order_id, merchant_id, cashier_id, status, total_amount) " +
                    "VALUES (?, 1, now(), ?, ?, ?, ?, ?)",
                    row.get("eventId"), row.get("orderId"), row.get("merchantId"),
                    row.get("cashierId"), row.get("status"), row.get("totalAmount"));
            }
            log.info("Flushed {} order events to ClickHouse", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush order buffer: {}", e.getMessage());
        }
    }

    @Transactional
    public void flushTransactionBuffer() {
        if (transactionBuffer.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> item;
        while ((item = transactionBuffer.poll()) != null) {
            batch.add(item);
        }
        try {
            for (Map<String, Object> row : batch) {
                clickhouseJdbc.update(
                    "INSERT INTO pos_stats.transaction_daily (event_id, event_version, occurred_at, " +
                    "transaction_id, order_id, merchant_id, payment_method, status, amount) " +
                    "VALUES (?, 1, now(), ?, ?, ?, ?, ?, ?)",
                    row.get("eventId"), row.get("transactionId"), row.get("orderId"),
                    row.get("merchantId"), row.get("paymentMethod"), row.get("status"), row.get("amount"));
            }
            log.info("Flushed {} transaction events to ClickHouse", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush transaction buffer: {}", e.getMessage());
        }
    }
}