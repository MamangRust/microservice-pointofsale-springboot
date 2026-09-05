package com.order.order.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class OrderRequest {
    private UUID productId;
    private Integer quantity;
} 