package com.transaction.transaction.controller;

import com.transaction.transaction.dto.TransactionMapper;
import com.transaction.transaction.dto.TransactionRequest;
import com.transaction.transaction.dto.TransactionResponse;
import com.transaction.transaction.service.TransactionService;
import com.transaction.transaction.entity.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transaction Management")
@SecurityRequirement(name = "Bearer Authentication")
public class TransactionController {
    private final TransactionService service;
    private final TransactionMapper mapper;
    public TransactionController(TransactionService service, TransactionMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all transactions")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/order/{orderId}") @Operation(summary = "Get transactions by order ID")
    public ResponseEntity<?> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(service.getByOrderId(orderId).stream().map(mapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}") @Operation(summary = "Get transaction by ID")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }

    @PostMapping @Operation(summary = "Create transaction")
    public ResponseEntity<?> create(@Valid @RequestBody TransactionRequest req) {
        try {
            Transaction txn = mapper.toEntity(req);
            Transaction saved = service.create(txn);
            return ResponseEntity.ok(mapper.toResponse(saved));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Duplicate")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/complete") @Operation(summary = "Complete transaction")
    public ResponseEntity<?> complete(@PathVariable Long id) {
        try { service.complete(id); return ResponseEntity.ok("Transaction completed"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }

    @PostMapping("/{id}/fail") @Operation(summary = "Fail transaction")
    public ResponseEntity<?> fail(@PathVariable Long id) {
        try { service.fail(id); return ResponseEntity.ok("Transaction failed"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}