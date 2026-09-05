package com.orderitem.orderitem.controller;

import com.orderitem.orderitem.dto.OrderItemMapper;
import com.orderitem.orderitem.dto.OrderItemRequest;
import com.orderitem.orderitem.service.OrderItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/order-items")
@Tag(name = "Order Item Management")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderItemController {
    private final OrderItemService service;
    private final OrderItemMapper mapper;
    public OrderItemController(OrderItemService service, OrderItemMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all order items")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/order/{orderId}") @Operation(summary = "Get order items by order ID")
    public ResponseEntity<?> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(service.getByOrderId(orderId).stream().map(mapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}") @Operation(summary = "Get order item by ID")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }

    @PostMapping @Operation(summary = "Create order item")
    public ResponseEntity<?> create(@Valid @RequestBody OrderItemRequest req) {
        return ResponseEntity.ok(mapper.toResponse(service.create(req)));
    }

    @PutMapping("/{id}") @Operation(summary = "Update order item")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody OrderItemRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.update(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }

    @DeleteMapping("/{id}") @Operation(summary = "Delete order item")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.ok("OrderItem deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}