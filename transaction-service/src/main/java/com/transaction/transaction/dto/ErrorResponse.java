package com.transaction.transaction.dto;

public record ErrorResponse(int status, String error, String message, String path) {}