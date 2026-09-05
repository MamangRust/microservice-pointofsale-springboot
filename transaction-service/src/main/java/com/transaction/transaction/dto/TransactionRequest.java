package com.transaction.transaction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TransactionRequest(
    @NotNull Long orderId,
    @NotNull Long merchantId,
    String paymentMethod,
    @NotNull @Min(0) Integer amount,
    String idempotencyKey
) {}