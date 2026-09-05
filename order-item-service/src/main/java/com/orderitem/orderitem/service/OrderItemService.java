package com.orderitem.orderitem.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Service;

import com.orderitem.orderitem.dto.OrderItemMapper;
import com.orderitem.orderitem.dto.OrderItemRequest;
import com.orderitem.orderitem.entity.OrderItem;
import com.orderitem.orderitem.repository.OrderItemRepository;

import java.util.List;

@Service
public class OrderItemService {
    private final OrderItemRepository repository;
    private final OrderItemMapper mapper;
    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestsDuration;

    public OrderItemService(OrderItemRepository repository, OrderItemMapper mapper, OpenTelemetry openTelemetry) {
        this.repository = repository;
        this.mapper = mapper;
        this.tracer = openTelemetry.getTracer("order-item-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-item-service");
        this.requestsTotal = meter.counterBuilder("requests_total").setDescription("Total requests").setUnit("1").build();
        this.requestsDuration = meter.histogramBuilder("requests_duration_seconds").setDescription("Duration").setUnit("s").build();
    }

    public List<OrderItem> getAll() {
        Span span = tracer.spanBuilder("getAllOrderItems").setSpanKind(SpanKind.SERVER).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return repository.findAll();
        } finally {
            span.end();
        }
    }

    public List<OrderItem> getByOrderId(Long orderId) {
        return repository.findByOrderId(orderId);
    }

    public OrderItem getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("OrderItem not found"));
    }

    public OrderItem create(OrderItemRequest req) {
        return repository.save(mapper.toEntity(req));
    }

    public OrderItem update(Long id, OrderItemRequest req) {
        OrderItem item = getById(id);
        item.setQuantity(req.quantity());
        item.setPrice(req.price());
        return repository.save(item);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}