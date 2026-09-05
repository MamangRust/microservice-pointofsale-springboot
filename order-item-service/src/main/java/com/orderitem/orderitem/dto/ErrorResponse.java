package com.orderitem.orderitem.dto;

public record ErrorResponse(int status, String error, String message, String path) {}