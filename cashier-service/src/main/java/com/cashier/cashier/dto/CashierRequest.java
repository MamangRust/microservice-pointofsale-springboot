package com.cashier.cashier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CashierRequest(
    @NotNull(message = "Merchant ID is required")
    Long merchantId,

    @NotNull(message = "User ID is required")
    Long userId,

    @NotBlank(message = "Cashier name is required")
    @Size(max = 100, message = "Cashier name must be at most 100 characters")
    String name
) {}
