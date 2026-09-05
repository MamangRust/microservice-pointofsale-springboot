package com.merchant.merchant.controller;

import com.merchant.merchant.dto.MerchantMapper;
import com.merchant.merchant.dto.MerchantRequest;
import com.merchant.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/merchants")
@Tag(name = "Merchant Management")
@SecurityRequirement(name = "Bearer Authentication")
public class MerchantController {
    private final MerchantService service;
    private final MerchantMapper mapper;
    public MerchantController(MerchantService service, MerchantMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping @Operation(summary = "Get all merchants")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll().stream().map(mapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}") @Operation(summary = "Get merchant by ID")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try { return ResponseEntity.ok(mapper.toResponse(service.getById(id))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }

    @PostMapping @Operation(summary = "Create merchant")
    public ResponseEntity<?> create(@Valid @RequestBody MerchantRequest req) {
        return ResponseEntity.ok(mapper.toResponse(service.create(req)));
    }

    @PutMapping("/{id}") @Operation(summary = "Update merchant")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody MerchantRequest req) {
        try { return ResponseEntity.ok(mapper.toResponse(service.update(id, req))); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }

    @DeleteMapping("/{id}") @Operation(summary = "Delete merchant")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.ok("Merchant deleted"); }
        catch (RuntimeException e) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage()); }
    }
}