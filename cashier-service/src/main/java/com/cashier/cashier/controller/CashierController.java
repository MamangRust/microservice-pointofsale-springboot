package com.cashier.cashier.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cashier.cashier.dto.CashierMapper;
import com.cashier.cashier.dto.CashierRequest;
import com.cashier.cashier.entity.Cashier;
import com.cashier.cashier.service.CashierService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cashiers")
@Tag(name = "Cashier Management", description = "Cashier CRUD with soft-delete")
@SecurityRequirement(name = "Bearer Authentication")
public class CashierController {
    private final CashierService cashierService;
    private final CashierMapper cashierMapper;

    public CashierController(CashierService cashierService, CashierMapper cashierMapper) {
        this.cashierService = cashierService;
        this.cashierMapper = cashierMapper;
    }

    @GetMapping
    @Operation(summary = "Get all cashiers")
    public ResponseEntity<?> getAllCashiers() {
        List<Cashier> cashiers = cashierService.getAllCashiers();
        return ResponseEntity.ok(cashiers.stream().map(cashierMapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "Get cashiers by merchant ID")
    public ResponseEntity<?> getCashiersByMerchantId(@PathVariable Long merchantId) {
        List<Cashier> cashiers = cashierService.getCashiersByMerchantId(merchantId);
        return ResponseEntity.ok(cashiers.stream().map(cashierMapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{cashierId}")
    @Operation(summary = "Get cashier by ID")
    public ResponseEntity<?> getCashierById(@PathVariable Long cashierId) {
        try {
            return ResponseEntity.ok(cashierMapper.toResponse(cashierService.getCashierById(cashierId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "Create a new cashier")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Cashier created"))
    public ResponseEntity<?> createCashier(@Valid @RequestBody CashierRequest request) {
        try {
            Cashier cashier = cashierService.createCashier(request);
            return ResponseEntity.ok(cashierMapper.toResponse(cashier));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/{cashierId}")
    @Operation(summary = "Update a cashier")
    public ResponseEntity<?> updateCashier(@PathVariable Long cashierId, @Valid @RequestBody CashierRequest request) {
        try {
            Cashier cashier = cashierService.updateCashier(cashierId, request);
            return ResponseEntity.ok(cashierMapper.toResponse(cashier));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/{cashierId}")
    @Operation(summary = "Soft-delete a cashier")
    public ResponseEntity<?> deleteCashier(@PathVariable Long cashierId) {
        try {
            cashierService.deleteCashier(cashierId);
            return ResponseEntity.ok("Cashier deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
