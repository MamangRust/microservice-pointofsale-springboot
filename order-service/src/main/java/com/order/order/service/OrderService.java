package com.order.order.service;

import java.util.List;
import java.util.UUID;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.order.client.ProductClient;
import com.order.order.client.UserClient;
import com.order.order.dto.OrderRequest;
import com.order.order.dto.Product;
import com.order.order.dto.UserDto;
import com.order.order.entity.Order;
import com.order.order.entity.PaymentStatusEnum;
import com.order.order.exc.AuthException;
import com.order.order.exc.ResourceNotFoundException;
import com.order.order.mapper.OrderMapper;
import com.order.order.repository.OrderRepository;

@Service
@Slf4j
public class OrderService {
        private final OrderRepository orderRepository;
        private final ProductClient productClient;
        private final UserClient userClient;
        private final OrderMapper orderMapper;
        private final Tracer tracer;
        private final Meter meter;

        // Metrics
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;
        private final LongCounter failureTotal;

        public OrderService(OrderRepository orderRepository,
                        ProductClient productClient,
                        UserClient userClient,
                        OrderMapper orderMapper,
                        OpenTelemetry openTelemetry) {
                log.info("Initializing OrderService with OpenTelemetry");

                this.orderRepository = orderRepository;
                this.productClient = productClient;
                this.userClient = userClient;
                this.orderMapper = orderMapper;
                this.tracer = openTelemetry.getTracer("order-service", "1.0.0");
                this.meter = openTelemetry.getMeter("order-service");

                log.debug("Setting up OpenTelemetry metrics");

                this.requestsTotal = meter
                                .counterBuilder("requests_total")
                                .setDescription("Total number of order requests")
                                .setUnit("1")
                                .build();

                this.requestDurationSeconds = meter
                                .histogramBuilder("requests_duration_seconds")
                                .setDescription("Order request duration in seconds")
                                .setUnit("s")
                                .build();

                this.failureTotal = meter
                                .counterBuilder("failure_total")
                                .setDescription("Total number of failed order requests")
                                .setUnit("1")
                                .build();

                log.info("OrderService initialized successfully");
        }

        public Product getProductById(UUID productId) {
                long startTime = System.nanoTime();
                String method = "getProductById";
                String status = "success";

                log.debug("Starting method: {} for productId: {}", method, productId);

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("product.id", productId.toString())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        log.info("Getting product by id: {}", productId);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service"));

                        Product product = productClient.getProductById(productId);

                        log.info("Successfully retrieved product: id={}, name={}", productId, product.getName());

                        span.setAttribute("product.name", product.getName());
                        span.setStatus(StatusCode.OK);

                        return product;
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        log.error("Failed to get product by id: {}, error: {}", productId, e.getMessage(), e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        log.debug("Method {} completed with status: {} in {} seconds", method, status, durationSeconds);

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "order-service");

                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        @Transactional
        public Order createOrder(OrderRequest orderRequest) {
                long startTime = System.nanoTime();
                String method = "createOrder";
                String status = "success";

                log.debug("Starting method: {} with request: {}", method, orderRequest);

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("order.product.id", orderRequest.getProductId().toString())
                                .setAttribute("order.quantity", orderRequest.getQuantity())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        log.info("Creating order for productId: {}, quantity: {}",
                                        orderRequest.getProductId(), orderRequest.getQuantity());

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service"));

                        Order order = orderMapper.toOrder(orderRequest);
                        log.debug("Order mapped from request: {}", order);

                        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                        log.debug("Authentication object retrieved: {}", authentication);

                        if (authentication == null || authentication.getPrincipal() == null) {
                                log.error("Authentication is null or principal is null");
                                throw new AuthException("User not authenticated");
                        }

                        String username = authentication.getName();
                        log.info("Authenticated user: {}", username);

                        if (username == null || username.isEmpty()) {
                                log.error("Username is null or empty");
                                throw new AuthException("Username not found in token");
                        }

                        if ("anonymousUser".equals(username)) {
                                log.error("User is anonymous - JWT token may be missing, invalid, or expired");
                                throw new AuthException(
                                                "User not properly authenticated. Please provide a valid JWT token in the Authorization header.");
                        }

                        log.info("Fetching user details for username: {}", username);

                        UserDto user = userClient.getUserByUsername(username);
                        if (user == null || user.getId() == null) {
                                log.error("User not found for username: {}", username);
                                throw new ResourceNotFoundException("User not found for username: " + username);
                        }

                        UUID userId = user.getId();
                        log.info("User ID resolved: {} for username: {}", userId, username);

                        order.setUserId(userId);
                        order.setPaymentStatus(PaymentStatusEnum.PENDING);
                        log.debug("Order userId and payment status set: userId={}, status=PENDING", userId);

                        log.info("Decreasing stock for productId: {}, quantity: {}",
                                        order.getProductId(), order.getQuantity());
                        productClient.decreaseStock(order.getProductId(), order.getQuantity());
                        log.info("Stock decreased successfully");

                        // Save the order
                        log.debug("Saving order to database");
                        Order savedOrder = orderRepository.save(order);

                        span.setAttribute("order.id", savedOrder.getId().toString());
                        span.setAttribute("order.user.id", userId.toString());
                        span.setStatus(StatusCode.OK);

                        log.info("Order created successfully: orderId={}, userId={}, productId={}, quantity={}",
                                        savedOrder.getId(), userId, savedOrder.getProductId(),
                                        savedOrder.getQuantity());

                        return savedOrder;
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        log.error("Failed to create order: {}, error: {}", orderRequest, e.getMessage(), e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        log.debug("Method {} completed with status: {} in {} seconds", method, status, durationSeconds);

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "order-service");

                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public boolean isOrderExist(UUID orderId) {
                long startTime = System.nanoTime();
                String method = "isOrderExist";
                String status = "success";

                log.debug("Starting method: {} for orderId: {}", method, orderId);

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("order.id", orderId.toString())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        log.info("Checking if order exists: orderId={}", orderId);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service"));

                        boolean exists = orderRepository.existsById(orderId);

                        span.setAttribute("order.exists", exists);
                        span.setStatus(StatusCode.OK);

                        log.info("Order existence check result: orderId={}, exists={}", orderId, exists);

                        return exists;
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        log.error("Failed to check if order exists: orderId={}, error: {}", orderId, e.getMessage(), e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        log.debug("Method {} completed with status: {} in {} seconds", method, status, durationSeconds);

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "order-service");

                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public void updateOrderPaymentStatus(UUID orderId, PaymentStatusEnum paymentStatus) {
                long startTime = System.nanoTime();
                String method = "updateOrderPaymentStatus";
                String status = "success";

                log.debug("Starting method: {} for orderId: {}, newStatus: {}", method, orderId, paymentStatus);

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("order.id", orderId.toString())
                                .setAttribute("order.payment.status", paymentStatus.toString())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        log.info("Updating payment status for order: orderId={}, newStatus={}", orderId, paymentStatus);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service"));

                        Order order = orderRepository.findById(orderId)
                                        .orElseThrow(() -> {
                                                log.error("Order not found: orderId={}", orderId);
                                                return new ResourceNotFoundException("Order not found");
                                        });

                        PaymentStatusEnum oldStatus = order.getPaymentStatus();
                        order.setPaymentStatus(paymentStatus);
                        orderRepository.save(order);

                        span.setAttribute("order.user.id", order.getUserId().toString());
                        span.setStatus(StatusCode.OK);

                        log.info("Payment status updated successfully: orderId={}, oldStatus={}, newStatus={}, userId={}",
                                        orderId, oldStatus, paymentStatus, order.getUserId());
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        log.error("Failed to update payment status: orderId={}, newStatus={}, error: {}",
                                        orderId, paymentStatus, e.getMessage(), e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        log.debug("Method {} completed with status: {} in {} seconds", method, status, durationSeconds);

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "order-service");

                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public List<Order> getOrdersByUserId() {
                long startTime = System.nanoTime();
                String method = "getOrdersByUserId";
                String status = "success";

                log.debug("Starting method: {}", method);

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service"));

                        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                        log.debug("Authentication object retrieved: {}", authentication);

                        if (authentication == null || authentication.getPrincipal() == null) {
                                log.error("Authentication is null or principal is null");
                                throw new AuthException("User not authenticated");
                        }

                        String username = authentication.getName();
                        log.info("Fetching orders for user: {}", username);

                        UserDto user = userClient.getUserByUsername(username);
                        UUID userId = user.getId();

                        span.setAttribute("user.id", userId.toString());
                        span.setAttribute("user.name", username);

                        log.info("Querying orders for userId: {}", userId);

                        List<Order> orders = orderRepository.findByUserId(userId);
                        if (orders == null || orders.isEmpty()) {
                                log.warn("No orders found for userId: {}", userId);
                                throw new ResourceNotFoundException("No orders found for user ID: " + userId);
                        }

                        span.setAttribute("orders.count", orders.size());
                        span.setStatus(StatusCode.OK);

                        log.info("Successfully retrieved {} orders for userId: {}", orders.size(), userId);

                        return orders;
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        log.error("Failed to get orders by user: error: {}", e.getMessage(), e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        log.debug("Method {} completed with status: {} in {} seconds", method, status, durationSeconds);

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "order-service");

                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public List<Order> getAllOrders() {
                long startTime = System.nanoTime();
                String method = "getAllOrders";
                String status = "success";

                log.debug("Starting method: {}", method);

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        log.info("Retrieving all orders");

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service"));

                        List<Order> orders = orderRepository.findAll();

                        span.setAttribute("orders.count", orders.size());
                        span.setStatus(StatusCode.OK);

                        log.info("Successfully retrieved {} orders", orders.size());

                        return orders;
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "order-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        log.error("Failed to get all orders: error: {}", e.getMessage(), e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        log.debug("Method {} completed with status: {} in {} seconds", method, status, durationSeconds);

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "order-service");

                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }
}