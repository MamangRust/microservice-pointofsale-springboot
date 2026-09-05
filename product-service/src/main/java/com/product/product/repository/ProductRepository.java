package com.product.product.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.product.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

}
