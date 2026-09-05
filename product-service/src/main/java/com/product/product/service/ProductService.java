package com.product.product.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.product.product.client.FileStorageClient;
import com.product.product.dto.ProductResponse;
import com.product.product.entity.Product;
import com.product.product.exc.InsufficientStockException;
import com.product.product.exc.InvalidRequestException;
import com.product.product.exc.ProductNotFoundException;
import com.product.product.mapper.ProductMapper;
import com.product.product.repository.ProductRepository;

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

@Service
public class ProductService {
        private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

        private final ProductRepository productRepository;
        private final ProductMapper productMapper;
        private final FileStorageClient fileStorageClient;
        private final Tracer tracer;
        private final Meter meter;

        // Metrics
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;
        private final LongCounter failureTotal;

        public ProductService(ProductRepository productRepository,
                        ProductMapper productMapper,
                        OpenTelemetry openTelemetry,
                        FileStorageClient fileStorageClient) {
                this.productRepository = productRepository;
                this.productMapper = productMapper;
                this.fileStorageClient = fileStorageClient;
                this.tracer = openTelemetry.getTracer("product-service", "1.0.0");
                this.meter = openTelemetry.getMeter("product-service");

                this.requestsTotal = meter
                                .counterBuilder("requests_total")
                                .setDescription("Total number of product requests")
                                .setUnit("1")
                                .build();

                this.requestDurationSeconds = meter
                                .histogramBuilder("requests_duration_seconds")
                                .setDescription("Product request duration in seconds")
                                .setUnit("s")
                                .build();

                this.failureTotal = meter
                                .counterBuilder("failure_total")
                                .setDescription("Total number of failed product requests")
                                .setUnit("1")
                                .build();
        }

        private String getImageUrl(UUID imageId) {
                if (imageId == null) {
                        return null;
                }
                return "/files/download/" + imageId;
        }

        public void createProduct(Product product) {
                long startTime = System.nanoTime();
                String method = "createProduct";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("product.name", product.getName())
                                .setAttribute("product.price", product.getPrice().toString())
                                .setAttribute("product.quantity", product.getQuantity())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Creating product: {}", product.getName());

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service"));

                        if (product.getImageId() != null) {
                                try {
                                        fileStorageClient.getFileMetadata(product.getImageId());
                                } catch (Exception e) {
                                        throw new InvalidRequestException("Associated image does not exist: " + product.getImageId());
                                }
                        }

                        Product savedProduct = productRepository.save(product);

                        span.setAttribute("product.id", savedProduct.getId().toString());
                        span.setStatus(StatusCode.OK);

                        logger.info("Product created successfully: productId={}, name={}",
                                        savedProduct.getId(), savedProduct.getName());
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        logger.error("Failed to create product: {}", product.getName(), e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "product-service");

                        requestsTotal.add(1, attributes);
                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public List<ProductResponse> getAllProducts() {
                long startTime = System.nanoTime();
                String method = "getAllProducts";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Getting all products");

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service"));

                        List<ProductResponse> products = productRepository.findAll()
                                        .stream()
                                        .map(product -> {
                                                ProductResponse res = productMapper.toProductResponse(product);
                                                res.setImageUrl(getImageUrl(product.getImageId()));
                                                return res;
                                        })
                                        .collect(Collectors.toList());

                        span.setAttribute("products.count", products.size());
                        span.setStatus(StatusCode.OK);

                        logger.info("Retrieved {} products successfully", products.size());
                        return products;
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        logger.error("Failed to get all products", e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "product-service");

                        requestsTotal.add(1, attributes);
                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public ProductResponse getProductById(UUID id) {
                long startTime = System.nanoTime();
                String method = "getProductById";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("product.id", id.toString())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Getting product by id: {}", id);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service"));

                        Product product = productRepository.findById(id)
                                        .orElseThrow(() -> {
                                                logger.error("Product not found: {}", id);
                                                return new ProductNotFoundException(id);
                                        });

                        ProductResponse productResponse = productMapper.toProductResponse(product);
                        productResponse.setImageUrl(getImageUrl(product.getImageId()));

                        span.setAttribute("product.name", product.getName());
                        span.setAttribute("product.price", product.getPrice().toString());
                        span.setStatus(StatusCode.OK);

                        logger.info("Product retrieved successfully: productId={}, name={}",
                                        id, product.getName());
                        return productResponse;
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        logger.error("Failed to get product by id: {}", id, e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "product-service");

                        requestsTotal.add(1, attributes);
                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public void updateProduct(UUID id, Product product) {
                long startTime = System.nanoTime();
                String method = "updateProduct";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("product.id", id.toString())
                                .setAttribute("product.name", product.getName())
                                .setAttribute("product.price", product.getPrice().toString())
                                .setAttribute("product.quantity", product.getQuantity())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Updating product by id: {}", id);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service"));

                        if (product.getImageId() != null) {
                                try {
                                        fileStorageClient.getFileMetadata(product.getImageId());
                                } catch (Exception e) {
                                        throw new InvalidRequestException("Associated image does not exist: " + product.getImageId());
                                }
                        }

                        Product existingProduct = productMapper.toProduct(getProductById(id));
                        existingProduct.setName(product.getName());
                        existingProduct.setDescription(product.getDescription());
                        existingProduct.setPrice(product.getPrice());
                        existingProduct.setQuantity(product.getQuantity());
                        existingProduct.setImageId(product.getImageId());

                        Product updatedProduct = productRepository.save(existingProduct);

                        span.setAttribute("product.name", updatedProduct.getName());
                        span.setStatus(StatusCode.OK);

                        logger.info("Product updated successfully: productId={}, name={}",
                                        id, updatedProduct.getName());
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        logger.error("Failed to update product by id: {}", id, e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "product-service");

                        requestsTotal.add(1, attributes);
                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public void deleteProduct(UUID id) {
                long startTime = System.nanoTime();
                String method = "deleteProduct";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("product.id", id.toString())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Deleting product by id: {}", id);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service"));

                        if (!productRepository.existsById(id)) {
                                throw new ProductNotFoundException(id);
                        }

                        productRepository.deleteById(id);

                        span.setStatus(StatusCode.OK);

                        logger.info("Product deleted successfully: productId={}", id);
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        logger.error("Failed to delete product by id: {}", id, e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "product-service");

                        requestsTotal.add(1, attributes);
                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public void decreaseStock(UUID id, Integer quantity) {
                long startTime = System.nanoTime();
                String method = "decreaseStock";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("product.id", id.toString())
                                .setAttribute("product.quantity.decrease", quantity)
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Decreasing stock for product by id: {}", id);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service"));

                        if (quantity <= 0) {
                                throw new InvalidRequestException("Quantity must be greater than 0");
                        }

                        Product product = productMapper.toProduct(getProductById(id));
                        span.setAttribute("product.current.quantity", product.getQuantity());

                        if (product.getQuantity() < quantity) {
                                throw new InsufficientStockException(product.getQuantity(), quantity);
                        }

                        product.setQuantity(product.getQuantity() - quantity);
                        Product updatedProduct = productRepository.save(product);

                        span.setAttribute("product.new.quantity", updatedProduct.getQuantity());
                        span.setStatus(StatusCode.OK);

                        logger.info("Stock decreased successfully: productId={}, oldQuantity={}, newQuantity={}",
                                        id, product.getQuantity() + quantity, updatedProduct.getQuantity());
                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "product-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        logger.error("Failed to decrease stock for product by id: {}", id, e);
                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "product-service");

                        requestsTotal.add(1, attributes);
                        requestDurationSeconds.record(durationSeconds, attributes);
                }
        }
}