package com.orderitem.orderitem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orderitem.orderitem.dto.OrderItemMapper;
import com.orderitem.orderitem.dto.OrderItemMapperImpl;
import com.orderitem.orderitem.dto.OrderItemRequest;
import com.orderitem.orderitem.entity.OrderItem;
import com.orderitem.orderitem.repository.OrderItemRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class OrderItemServiceTest {

    @Mock
    private OrderItemRepository repository;

    private OrderItemService orderItemService;

    private final OrderItemMapper mapper = new OrderItemMapperImpl();

    @BeforeEach
    void setUp() {
        orderItemService = new OrderItemService(repository, mapper, OpenTelemetry.noop());
    }

    private OrderItemRequest request(Long orderId, Long productId, Integer quantity, Integer price) {
        return new OrderItemRequest(orderId, productId, quantity, price);
    }

    private OrderItem item(Long id, Long orderId, Long productId, Integer quantity, Integer price) {
        OrderItem item = new OrderItem();
        item.setOrderItemId(id);
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(price);
        return item;
    }

    @Test
    void getAll_returnsAllFromRepository() {
        when(repository.findAll()).thenReturn(List.of(
                item(1L, 10L, 100L, 2, 500),
                item(2L, 10L, 101L, 1, 250)));

        List<OrderItem> result = orderItemService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrderItem::getOrderItemId).containsExactly(1L, 2L);
        verify(repository).findAll();
    }

    @Test
    void getAll_returnsEmptyListWhenNone() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(orderItemService.getAll()).isEmpty();
    }

    @Test
    void getByOrderId_returnsFromRepository() {
        when(repository.findByOrderId(10L)).thenReturn(List.of(item(1L, 10L, 100L, 2, 500)));

        List<OrderItem> result = orderItemService.getByOrderId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo(10L);
        verify(repository).findByOrderId(10L);
    }

    @Test
    void getByOrderId_returnsEmptyWhenNoMatch() {
        when(repository.findByOrderId(999L)).thenReturn(List.of());

        assertThat(orderItemService.getByOrderId(999L)).isEmpty();
    }

    @Test
    void getById_returnsItemWhenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(item(1L, 10L, 100L, 2, 500)));

        OrderItem result = orderItemService.getById(1L);

        assertThat(result.getOrderItemId()).isEqualTo(1L);
        assertThat(result.getPrice()).isEqualTo(500);
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderItemService.getById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("OrderItem not found");
    }

    @Test
    void create_mapsRequestToEntityAndSaves() {
        OrderItem saved = item(7L, 10L, 100L, 2, 500);
        when(repository.save(any(OrderItem.class))).thenReturn(saved);

        OrderItem result = orderItemService.create(request(10L, 100L, 2, 500));

        assertThat(result.getOrderItemId()).isEqualTo(7L);

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(10L);
        assertThat(captor.getValue().getProductId()).isEqualTo(100L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
        assertThat(captor.getValue().getPrice()).isEqualTo(500);
    }

    @Test
    void update_setsQuantityAndPriceOnExistingItem() {
        OrderItem existing = item(1L, 10L, 100L, 2, 500);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderItem result = orderItemService.update(1L, request(10L, 100L, 5, 750));

        assertThat(result.getQuantity()).isEqualTo(5);
        assertThat(result.getPrice()).isEqualTo(750);
        // orderId/productId are not touched by update
        assertThat(result.getOrderId()).isEqualTo(10L);
        assertThat(result.getProductId()).isEqualTo(100L);
        verify(repository).save(existing);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderItemService.update(99L, request(10L, 100L, 1, 100)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("OrderItem not found");

        verify(repository, org.mockito.Mockito.never()).save(any(OrderItem.class));
    }

    @Test
    void delete_delegatesToRepository() {
        orderItemService.delete(1L);

        verify(repository).deleteById(1L);
    }
}
