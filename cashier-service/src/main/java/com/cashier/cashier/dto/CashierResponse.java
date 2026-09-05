package com.cashier.cashier.dto;

import java.time.LocalDateTime;

public record CashierResponse(
    Long cashierId,
    Long merchantId,
    Long userId,
    String name,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
