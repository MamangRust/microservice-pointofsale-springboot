package com.merchant.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantRequest(
    @NotBlank @Size(max = 100) String name,
    String description,
    String address,
    String contactEmail,
    String contactPhone
) {}