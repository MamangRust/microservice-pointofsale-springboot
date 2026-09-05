package com.role.role.dto;

import java.time.LocalDateTime;

public record RoleResponse(
    Long id,
    String roleName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}