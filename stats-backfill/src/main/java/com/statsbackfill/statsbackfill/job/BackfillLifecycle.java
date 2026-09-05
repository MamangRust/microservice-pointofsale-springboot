package com.statsbackfill.statsbackfill.job;

import com.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-shot job mirroring Quarkus BackfillLifecycle:
 * reads OLTP PostgreSQL (orders, transactions from BACKFILL_FROM date),
 * publishes events to Kafka topics so stats-writer replays them into ClickHouse.
 */
@Component
public class BackfillLifecycle implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillLifecycle.class);

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${backfill.from:2020-01-01}")
    private String backfillFrom;

    public BackfillLifecycle(JdbcTemplate jdbcTemplate,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LocalDate from = LocalDate.parse(backfillFrom);
        log.info("Starting stats backfill from {}", from);

        backfillOrders(from);
        backfillTransactions(from);

        log.info("Stats backfill completed");
        // one-shot: exit after done (docker compose restart: "no")
        System.exit(0);
    }

    private void backfillOrders(LocalDate from) {
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
            "SELECT order_id, merchant_id, cashier_id, total_price, created_at " +
            "FROM pos_order.orders WHERE created_at >= ?", from);
        log.info("Backfilling {} orders", orders.size());
        for (Map<String, Object> o : orders) {
            var payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "orderId", o.get("order_id"),
                "merchantId", o.get("merchant_id"),
                "cashierId", o.get("cashier_id"),
                "status", "SUCCESS",
                "totalAmount", o.get("total_price"),
                "occurredAt", o.get("created_at").toString()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "order.completed", "order");
            try {
                kafkaTemplate.send("stats.pos.order.event", String.valueOf(o.get("order_id")),
                    objectMapper.writeValueAsString(envelope));
                log.debug("Published order event {}", o.get("order_id"));
            } catch (Exception e) {
                log.error("Failed to publish order event {}: {}", o.get("order_id"), e.getMessage());
            }
        }
    }

    private void backfillTransactions(LocalDate from) {
        List<Map<String, Object>> txns = jdbcTemplate.queryForList(
            "SELECT transaction_id, order_id, merchant_id, payment_method, amount, status, created_at " +
            "FROM pos_transaction.transactions WHERE created_at >= ?", from);
        log.info("Backfilling {} transactions", txns.size());
        for (Map<String, Object> t : txns) {
            var payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "transactionId", t.get("transaction_id"),
                "orderId", t.get("order_id"),
                "merchantId", t.get("merchant_id"),
                "paymentMethod", t.get("payment_method"),
                "status", t.get("status"),
                "amount", t.get("amount"),
                "occurredAt", t.get("created_at").toString()
            );
            EventEnvelope<Map<String, Object>> envelope =
                EventEnvelope.withDefaults(payload, "transaction.completed", "transaction");
            try {
                kafkaTemplate.send("stats.pos.transaction.event", String.valueOf(t.get("transaction_id")),
                    objectMapper.writeValueAsString(envelope));
                log.debug("Published transaction event {}", t.get("transaction_id"));
            } catch (Exception e) {
                log.error("Failed to publish transaction event {}: {}", t.get("transaction_id"), e.getMessage());
            }
        }
    }
}