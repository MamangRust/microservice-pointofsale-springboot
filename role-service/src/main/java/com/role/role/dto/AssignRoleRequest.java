package com.role.role.dto;

import java.util.List;

public record AssignRoleRequest(
    Long userId,
    List<Long> roleIds
) {}