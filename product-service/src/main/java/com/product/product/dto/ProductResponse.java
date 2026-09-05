package com.product.product.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class ProductResponse {
    private UUID id;
    private String name;
    private String description;
    private Double price;
    private Integer quantity;
    private UUID imageId;
    private String imageUrl;
}
