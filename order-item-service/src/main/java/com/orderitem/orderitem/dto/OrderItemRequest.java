package com.orderitem.orderitem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
    @NotNull Long orderId,
    @NotNull Long productId,
    @NotNull @Min(1) Integer quantity,
    @NotNull @Min(0) Integer price
) {}