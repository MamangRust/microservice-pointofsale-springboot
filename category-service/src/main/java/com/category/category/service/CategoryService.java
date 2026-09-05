package com.category.category.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.category.category.dto.CategoryMapper;
import com.category.category.dto.CategoryRequest;
import com.category.category.entity.Category;
import com.category.category.repository.CategoryRepository;

import java.util.List;

@Service
public class CategoryService {
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);
    private final CategoryRepository repository;
    private final CategoryMapper mapper;
    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestsDuration;

    public CategoryService(CategoryRepository repository, CategoryMapper mapper, OpenTelemetry openTelemetry) {
        this.repository = repository;
        this.mapper = mapper;
        this.tracer = openTelemetry.getTracer("category-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("category-service");
        this.requestsTotal = meter.counterBuilder("requests_total").setDescription("Total requests").setUnit("1").build();
        this.requestsDuration = meter.histogramBuilder("requests_duration_seconds").setDescription("Duration").setUnit("s").build();
    }

    public List<Category> getAll() {
        Span span = tracer.spanBuilder("getAllCategories").setSpanKind(SpanKind.SERVER).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return repository.findAll();
        } finally {
            span.end();
        }
    }

    public Category getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
    }

    public Category create(CategoryRequest req) {
        if (repository.findBySlugCategory(req.name().toLowerCase().replaceAll("\\s+", "-")).isPresent()) {
            throw new RuntimeException("Category already exists");
        }
        return repository.save(mapper.toEntity(req));
    }

    public Category update(Long id, CategoryRequest req) {
        Category c = getById(id);
        c.setName(req.name());
        c.setDescription(req.description());
        return repository.save(c);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}