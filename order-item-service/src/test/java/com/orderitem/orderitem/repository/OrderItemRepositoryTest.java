package com.orderitem.orderitem.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orderitem.orderitem.entity.OrderItem;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OrderItemRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private OrderItemRepository orderItemRepository;

    private OrderItem item(Long orderId, Long productId, Integer quantity, Integer price) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(price);
        return item;
    }

    @Test
    void save_persistsOrderItemWithGeneratedIdAndTimestamps() {
        OrderItem saved = orderItemRepository.save(item(10L, 100L, 2, 500));

        assertThat(saved.getOrderItemId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void findById_returnsSavedOrderItem() {
        OrderItem saved = orderItemRepository.save(item(10L, 100L, 3, 750));

        Optional<OrderItem> found = orderItemRepository.findById(saved.getOrderItemId());

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(10L);
        assertThat(found.get().getProductId()).isEqualTo(100L);
        assertThat(found.get().getQuantity()).isEqualTo(3);
        assertThat(found.get().getPrice()).isEqualTo(750);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(orderItemRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findByOrderId_returnsOnlyThatOrdersItems() {
        orderItemRepository.save(item(10L, 100L, 1, 500));
        orderItemRepository.save(item(10L, 101L, 2, 250));
        orderItemRepository.save(item(20L, 102L, 3, 100));

        List<OrderItem> result = orderItemRepository.findByOrderId(10L);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(i -> assertThat(i.getOrderId()).isEqualTo(10L));
        assertThat(result).extracting(OrderItem::getProductId)
                .containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    void findByOrderId_returnsEmptyWhenNoMatch() {
        orderItemRepository.save(item(10L, 100L, 1, 500));

        assertThat(orderItemRepository.findByOrderId(999L)).isEmpty();
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        OrderItem saved = orderItemRepository.save(item(10L, 100L, 1, 500));

        saved.setQuantity(9);
        OrderItem updated = orderItemRepository.saveAndFlush(saved);

        assertThat(updated.getQuantity()).isEqualTo(9);
        assertThat(updated.getUpdatedAt())
                .isAfterOrEqualTo(updated.getCreatedAt());
        assertThat(orderItemRepository.findById(saved.getOrderItemId()).orElseThrow().getQuantity())
                .isEqualTo(9);
    }

    @Test
    void deleteById_removesRow() {
        OrderItem saved = orderItemRepository.save(item(10L, 100L, 1, 500));

        orderItemRepository.deleteById(saved.getOrderItemId());
        orderItemRepository.flush();

        assertThat(orderItemRepository.findById(saved.getOrderItemId())).isEmpty();
    }
}
