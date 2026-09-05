package com.merchant.merchant.dto;

import java.time.LocalDateTime;

public record MerchantResponse(
    Long merchantId, Long userId, String merchantNo, String apiKey,
    String name, String description, String address,
    String contactEmail, String contactPhone, String status,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}