package com.category.category.controller;

import com.category.category.dto.CategoryMapper;
import com.category.category.dto.CategoryRequest;
import com.category.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/categories")
@Tag(name = "Category Management")
@SecurityRequirement(name = "Bearer Authentication")
public class CategoryController {
    private final CategoryService service;
    private final CategoryMapper mapper;
    public CategoryController(CategoryService service, CategoryMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all categories")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}") @Operation(summary = "Get category by ID")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }

    @PostMapping @Operation(summary = "Create category")
    public ResponseEntity<?> create(@Valid @RequestBody CategoryRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.create(req))); }
        catch (RuntimeException e) {
            if (e.getMessage().contains("already exists")) return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/{id}") @Operation(summary = "Update category")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.update(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }

    @DeleteMapping("/{id}") @Operation(summary = "Delete category")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.ok("Category deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}