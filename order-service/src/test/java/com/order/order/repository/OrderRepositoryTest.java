package com.order.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.order.order.entity.Order;
import com.order.order.entity.Outbox;
import com.order.order.entity.OutboxStatus;
import com.order.order.entity.PaymentStatusEnum;
import com.order.order.repository.OutboxRepository;

@DataJpaTest(properties = {
        // no outbox migration exists; ddl-auto=update lets hibernate create the outbox table
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Order order(UUID productId, UUID userId, Integer quantity, PaymentStatusEnum status) {
        Order order = new Order();
        order.setProductId(productId);
        order.setUserId(userId);
        order.setQuantity(quantity);
        order.setPaymentStatus(status);
        return order;
    }

    @Test
    void save_persistsOrderWithGeneratedUuid() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Order saved = orderRepository.save(order(productId, userId, 3, PaymentStatusEnum.PENDING));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatusEnum.PENDING);

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getProductId()).isEqualTo(productId);
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getQuantity()).isEqualTo(3);
    }

    @Test
    void save_persistsAllPaymentStatusEnumValuesAsString() {
        Order pending = orderRepository.save(order(UUID.randomUUID(), UUID.randomUUID(), 1, PaymentStatusEnum.PENDING));
        Order completed = orderRepository.save(order(UUID.randomUUID(), UUID.randomUUID(), 2, PaymentStatusEnum.COMPLETED));
        Order refunded = orderRepository.save(order(UUID.randomUUID(), UUID.randomUUID(), 3, PaymentStatusEnum.REFUNDED));

        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatusEnum.PENDING);
        assertThat(orderRepository.findById(completed.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatusEnum.COMPLETED);
        assertThat(orderRepository.findById(refunded.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatusEnum.REFUNDED);
    }

    @Test
    void findByUserId_returnsOnlyThatUsersOrders() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        orderRepository.save(order(UUID.randomUUID(), userId, 1, PaymentStatusEnum.PENDING));
        orderRepository.save(order(UUID.randomUUID(), userId, 2, PaymentStatusEnum.COMPLETED));
        orderRepository.save(order(UUID.randomUUID(), otherUserId, 3, PaymentStatusEnum.PENDING));

        List<Order> result = orderRepository.findByUserId(userId);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(o -> assertThat(o.getUserId()).isEqualTo(userId));
    }

    @Test
    void findByUserId_returnsEmptyWhenUserHasNoOrders() {
        orderRepository.save(order(UUID.randomUUID(), UUID.randomUUID(), 1, PaymentStatusEnum.PENDING));

        List<Order> result = orderRepository.findByUserId(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void update_changesPaymentStatusInPlace() {
        Order saved = orderRepository.save(order(UUID.randomUUID(), UUID.randomUUID(), 1, PaymentStatusEnum.PENDING));

        saved.setPaymentStatus(PaymentStatusEnum.COMPLETED);
        Order updated = orderRepository.saveAndFlush(saved);

        assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatusEnum.COMPLETED);
        assertThat(orderRepository.findById(saved.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatusEnum.COMPLETED);
    }

    @Test
    void deleteById_removesOrder() {
        Order saved = orderRepository.save(order(UUID.randomUUID(), UUID.randomUUID(), 1, PaymentStatusEnum.PENDING));

        orderRepository.deleteById(saved.getId());
        orderRepository.flush();

        assertThat(orderRepository.findById(saved.getId())).isEmpty();
    }

    private Outbox outbox(String eventId) {
        Outbox outbox = new Outbox();
        outbox.setAggregateType("Order");
        outbox.setAggregateId(UUID.randomUUID().toString());
        outbox.setTopic("order-events");
        outbox.setPayload("{\"orderId\":\"" + UUID.randomUUID() + "\"}");
        outbox.setEventId(eventId);
        return outbox;
    }

    @Test
    void outbox_save_appliesDefaultsAndPrePersistCreatedAt() {
        Outbox saved = outboxRepository.save(outbox(java.util.UUID.randomUUID().toString()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getProcessedAt()).isNull();
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    void outbox_findByStatusOrderByCreatedAt_returnsPendingSortedOldestFirst() {
        Outbox older = outboxRepository.save(outbox(java.util.UUID.randomUUID().toString()));
        Outbox newer = outboxRepository.save(outbox(java.util.UUID.randomUUID().toString()));

        List<Outbox> pending = outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING);

        assertThat(pending).extracting(Outbox::getEventId)
                .containsExactly(older.getEventId(), newer.getEventId());

        newer.setStatus(OutboxStatus.PROCESSED);
        outboxRepository.saveAndFlush(newer);

        assertThat(outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING))
                .extracting(Outbox::getEventId)
                .containsExactly(older.getEventId());
        assertThat(outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PROCESSED))
                .extracting(Outbox::getEventId)
                .containsExactly(newer.getEventId());
        assertThat(outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.FAILED)).isEmpty();
    }
}
