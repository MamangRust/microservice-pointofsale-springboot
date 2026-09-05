package com.category.category.dto;

import java.time.LocalDateTime;

public record CategoryResponse(
    Long categoryId, String name, String description, String slugCategory,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}