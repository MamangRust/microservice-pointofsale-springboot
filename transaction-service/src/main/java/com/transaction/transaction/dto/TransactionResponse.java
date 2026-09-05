package com.transaction.transaction.dto;

import java.time.LocalDateTime;

public record TransactionResponse(
    Long transactionId, Long orderId, Long merchantId, String paymentMethod,
    Integer amount, Integer changeAmount, String status,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}