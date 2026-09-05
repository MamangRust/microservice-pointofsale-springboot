package com.orderitem.orderitem.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orderitem.orderitem.entity.OrderItem;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
}
