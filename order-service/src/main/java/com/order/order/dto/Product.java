package com.order.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class Product {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer quantity;
}
