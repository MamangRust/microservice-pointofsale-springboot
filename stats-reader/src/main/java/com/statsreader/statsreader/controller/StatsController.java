package com.statsreader.statsreader.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.statsreader.statsreader.service.StatsQueryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stats")
@Tag(name = "Stats Reader", description = "Stats query endpoints backed by ClickHouse")
public class StatsController {

    private final StatsQueryService statsQueryService;

    public StatsController(StatsQueryService statsQueryService) {
        this.statsQueryService = statsQueryService;
    }

    // === Cashier stats ===

    @GetMapping("/monthly-total-sales")
    @Operation(summary = "Cashier monthly total sales")
    public ResponseEntity<List<Map<String, Object>>> cashierMonthlyTotalSales(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(statsQueryService.cashierMonthlyTotalSales(year, month));
    }

    @GetMapping("/yearly-total-sales")
    @Operation(summary = "Cashier yearly total sales")
    public ResponseEntity<List<Map<String, Object>>> cashierYearlyTotalSales(@RequestParam int year) {
        return ResponseEntity.ok(statsQueryService.cashierYearlyTotalSales(year));
    }

    @GetMapping("/monthly-sales")
    @Operation(summary = "Cashier monthly sales by year")
    public ResponseEntity<List<Map<String, Object>>> cashierMonthlySales(@RequestParam int year) {
        return ResponseEntity.ok(statsQueryService.cashierMonthlySales(year));
    }

    @GetMapping("/yearly-sales")
    @Operation(summary = "Cashier yearly sales")
    public ResponseEntity<List<Map<String, Object>>> cashierYearlySales() {
        return ResponseEntity.ok(statsQueryService.cashierYearlySales());
    }

    // === Category stats ===

    @GetMapping("/monthly-total-prices")
    @Operation(summary = "Category monthly total prices")
    public ResponseEntity<List<Map<String, Object>>> categoryMonthlyTotalPrices(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(statsQueryService.categoryMonthlyTotalPrices(year, month));
    }

    // === Order stats ===

    @GetMapping("/monthly-total-revenue")
    @Operation(summary = "Order monthly total revenue")
    public ResponseEntity<List<Map<String, Object>>> orderMonthlyTotalRevenue(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(statsQueryService.orderMonthlyTotalRevenue(year, month));
    }

    @GetMapping("/yearly-total-revenue")
    @Operation(summary = "Order yearly total revenue")
    public ResponseEntity<List<Map<String, Object>>> orderYearlyTotalRevenue(@RequestParam int year) {
        return ResponseEntity.ok(statsQueryService.orderYearlyTotalRevenue(year));
    }

    // === Transaction stats ===

    @GetMapping("/amount/monthly")
    @Operation(summary = "Transaction amount monthly")
    public ResponseEntity<List<Map<String, Object>>> transactionAmountMonthly(@RequestParam int year) {
        return ResponseEntity.ok(statsQueryService.transactionAmountMonthly(year));
    }

    @GetMapping("/method/monthly")
    @Operation(summary = "Transaction method monthly")
    public ResponseEntity<List<Map<String, Object>>> transactionMethodMonthly(@RequestParam int year) {
        return ResponseEntity.ok(statsQueryService.transactionMethodMonthly(year));
    }

    @GetMapping("/status/monthly/success")
    @Operation(summary = "Transaction success status monthly")
    public ResponseEntity<List<Map<String, Object>>> transactionStatusSuccess(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(statsQueryService.transactionStatusMonthly(year, month, "SUCCESS"));
    }

    @GetMapping("/status/monthly/failed")
    @Operation(summary = "Transaction failed status monthly")
    public ResponseEntity<List<Map<String, Object>>> transactionStatusFailed(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(statsQueryService.transactionStatusMonthly(year, month, "FAILED"));
    }
}