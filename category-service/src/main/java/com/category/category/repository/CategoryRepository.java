package com.category.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.category.category.entity.Category;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlugCategory(String slugCategory);
}
