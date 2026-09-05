package com.cashier.cashier.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cashier.cashier.dto.CashierMapper;
import com.cashier.cashier.dto.CashierRequest;
import com.cashier.cashier.entity.Cashier;
import com.cashier.cashier.repository.CashierRepository;

import java.util.List;

@Service
public class CashierService {
    private static final Logger log = LoggerFactory.getLogger(CashierService.class);

    private final CashierRepository cashierRepository;
    private final CashierMapper cashierMapper;
    private final Tracer tracer;
    private final Meter meter;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestsDurationSeconds;
    private final LongCounter failureTotal;

    public CashierService(CashierRepository cashierRepository,
                          CashierMapper cashierMapper,
                          OpenTelemetry openTelemetry) {
        this.cashierRepository = cashierRepository;
        this.cashierMapper = cashierMapper;
        this.tracer = openTelemetry.getTracer("cashier-service", "1.0.0");
        this.meter = openTelemetry.getMeter("cashier-service");
        this.requestsTotal = meter.counterBuilder("requests_total")
            .setDescription("Total requests").setUnit("1").build();
        this.requestsDurationSeconds = meter.histogramBuilder("requests_duration_seconds")
            .setDescription("Request duration").setUnit("s").build();
        this.failureTotal = meter.counterBuilder("failure_total")
            .setDescription("Total failures").setUnit("1").build();
    }

    public List<Cashier> getAllCashiers() {
        Span span = tracer.spanBuilder("CashierService.getAllCashiers")
            .setSpanKind(SpanKind.INTERNAL).startSpan();
        long startTime = System.nanoTime();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("db.operation", "SELECT");
            List<Cashier> cashiers = cashierRepository.findAll();
            span.setAttribute("cashier.count", cashiers.size());
            return cashiers;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            failureTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "getAllCashiers"));
            throw e;
        } finally {
            span.end();
            requestsTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "getAllCashiers"));
            requestsDurationSeconds.record((System.nanoTime() - startTime) / 1_000_000_000.0,
                Attributes.of(AttributeKey.stringKey("operation"), "getAllCashiers"));
        }
    }

    public Cashier getCashierById(Long cashierId) {
        Span span = tracer.spanBuilder("CashierService.getCashierById")
            .setSpanKind(SpanKind.INTERNAL).startSpan();
        long startTime = System.nanoTime();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("cashier.id", cashierId);
            return cashierRepository.findById(cashierId)
                .orElseThrow(() -> new RuntimeException("Cashier not found: " + cashierId));
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            failureTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "getCashierById"));
            throw e;
        } finally {
            span.end();
            requestsTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "getCashierById"));
            requestsDurationSeconds.record((System.nanoTime() - startTime) / 1_000_000_000.0,
                Attributes.of(AttributeKey.stringKey("operation"), "getCashierById"));
        }
    }

    public Cashier createCashier(CashierRequest request) {
        Span span = tracer.spanBuilder("CashierService.createCashier")
            .setSpanKind(SpanKind.INTERNAL).startSpan();
        long startTime = System.nanoTime();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("cashier.name", request.name());
            span.setAttribute("cashier.merchantId", request.merchantId());
            span.setAttribute("cashier.userId", request.userId());
            Cashier cashier = cashierMapper.toEntity(request);
            return cashierRepository.save(cashier);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            failureTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "createCashier"));
            throw e;
        } finally {
            span.end();
            requestsTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "createCashier"));
            requestsDurationSeconds.record((System.nanoTime() - startTime) / 1_000_000_000.0,
                Attributes.of(AttributeKey.stringKey("operation"), "createCashier"));
        }
    }

    public Cashier updateCashier(Long cashierId, CashierRequest request) {
        Span span = tracer.spanBuilder("CashierService.updateCashier")
            .setSpanKind(SpanKind.INTERNAL).startSpan();
        long startTime = System.nanoTime();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("cashier.id", cashierId);
            Cashier cashier = getCashierById(cashierId);
            cashier.setMerchantId(request.merchantId());
            cashier.setUserId(request.userId());
            cashier.setName(request.name());
            return cashierRepository.save(cashier);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            failureTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "updateCashier"));
            throw e;
        } finally {
            span.end();
            requestsTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "updateCashier"));
            requestsDurationSeconds.record((System.nanoTime() - startTime) / 1_000_000_000.0,
                Attributes.of(AttributeKey.stringKey("operation"), "updateCashier"));
        }
    }

    @Transactional
    public void deleteCashier(Long cashierId) {
        Span span = tracer.spanBuilder("CashierService.deleteCashier")
            .setSpanKind(SpanKind.INTERNAL).startSpan();
        long startTime = System.nanoTime();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("cashier.id", cashierId);
            Cashier cashier = getCashierById(cashierId);
            cashier.setDeletedAt(java.time.LocalDateTime.now());
            cashierRepository.save(cashier);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            failureTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "deleteCashier"));
            throw e;
        } finally {
            span.end();
            requestsTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "deleteCashier"));
            requestsDurationSeconds.record((System.nanoTime() - startTime) / 1_000_000_000.0,
                Attributes.of(AttributeKey.stringKey("operation"), "deleteCashier"));
        }
    }

    public List<Cashier> getCashiersByMerchantId(Long merchantId) {
        Span span = tracer.spanBuilder("CashierService.getCashiersByMerchantId")
            .setSpanKind(SpanKind.INTERNAL).startSpan();
        long startTime = System.nanoTime();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("cashier.merchantId", merchantId);
            List<Cashier> cashiers = cashierRepository.findByMerchantId(merchantId);
            span.setAttribute("cashier.count", cashiers.size());
            return cashiers;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            failureTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "getCashiersByMerchantId"));
            throw e;
        } finally {
            span.end();
            requestsTotal.add(1, Attributes.of(AttributeKey.stringKey("operation"), "getCashiersByMerchantId"));
            requestsDurationSeconds.record((System.nanoTime() - startTime) / 1_000_000_000.0,
                Attributes.of(AttributeKey.stringKey("operation"), "getCashiersByMerchantId"));
        }
    }
}
