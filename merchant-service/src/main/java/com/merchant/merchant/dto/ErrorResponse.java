package com.merchant.merchant.dto;

public record ErrorResponse(int status, String error, String message, String path) {}