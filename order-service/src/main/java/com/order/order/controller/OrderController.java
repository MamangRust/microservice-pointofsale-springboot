package com.order.order.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.order.order.config.RabbitConfig;
import com.order.order.dto.Product;
import com.order.order.dto.OrderRequest;
import com.order.order.entity.Order;
import com.order.order.entity.PaymentStatusEnum;
import com.order.order.exc.InvalidRequestException;
import com.order.order.mapper.OrderMapper;
import com.order.order.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "Order management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order and sends it to payment processing queue")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or product not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest orderRequest) {
        try {
            if (orderRequest.getProductId() == null) {
                throw new InvalidRequestException("Product ID is required");
            }
            if (orderRequest.getQuantity() == null || orderRequest.getQuantity() <= 0) {
                throw new InvalidRequestException("Quantity must be a positive number");
            }

            Product product = orderService.getProductById(orderRequest.getProductId());
            if (product == null) {
                throw new InvalidRequestException("Product not found");
            }
        } catch (Exception e) {
            throw new InvalidRequestException("Failed to verify product: " + e.getMessage());
        }

        Order createdOrder = orderService.createOrder(orderRequest);

        rabbitTemplate.convertAndSend(RabbitConfig.QUEUE, createdOrder);

        return ResponseEntity.ok(createdOrder);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Check if order exists", description = "Checks if an order exists by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order existence check completed"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public boolean getOrderById(@Parameter(description = "Order ID") @PathVariable UUID orderId) {
        return orderService.isOrderExist(orderId);
    }

    @PutMapping("/{orderId}")
    @Operation(summary = "Update order payment status", description = "Updates the payment status of an order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "400", description = "Invalid payment status")
    })
    public void updateOrderPaymentStatus(
            @Parameter(description = "Order ID") @PathVariable UUID orderId,
            @RequestBody PaymentStatusEnum paymentStatus) {
        orderService.updateOrderPaymentStatus(orderId, paymentStatus);
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Get user orders", description = "Retrieves orders for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User orders retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<Order>> getOrdersByUserId() {
        List<Order> orders = orderService.getOrdersByUserId();
        return ResponseEntity.ok(orders);
    }

    @GetMapping
    @Operation(summary = "Get all orders", description = "Retrieves all orders in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All orders retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }
}
