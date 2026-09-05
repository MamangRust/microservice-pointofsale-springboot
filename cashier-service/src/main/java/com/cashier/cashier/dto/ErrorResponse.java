package com.cashier.cashier.dto;

public record ErrorResponse(
    int status,
    String error,
    String message,
    String path
) {}
