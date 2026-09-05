package com.orderitem.orderitem.dto;

import java.time.LocalDateTime;

public record OrderItemResponse(
    Long orderItemId, Long orderId, Long productId, Integer quantity, Integer price,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}