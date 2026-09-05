package api.gateway.api_gateway.filter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import api.gateway.api_gateway.util.JwtUtil;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

@Component
public class JwtAuthFilter implements GatewayFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final Tracer tracer;
    private final Meter meter;

    private final LongCounter authRequestsTotal;
    private final LongCounter authSuccessCounter;
    private final LongCounter authFailureCounter;
    private final LongCounter authMissingHeaderCounter;
    private final LongCounter authInvalidTokenCounter;

    public JwtAuthFilter(JwtUtil jwtUtil, OpenTelemetry openTelemetry) {
        this.jwtUtil = jwtUtil;
        this.tracer = openTelemetry.getTracer("jwt-auth-filter", "1.0.0");
        this.meter = openTelemetry.getMeter("jwt-auth-filter");

        this.authRequestsTotal = meter
                .counterBuilder("auth_requests_total")
                .setDescription("Total number of authentication requests")
                .setUnit("1")
                .build();

        this.authSuccessCounter = meter
                .counterBuilder("auth_success_total")
                .setDescription("Total number of successful authentications")
                .setUnit("1")
                .build();

        this.authFailureCounter = meter
                .counterBuilder("auth_failure_total")
                .setDescription("Total number of failed authentications")
                .setUnit("1")
                .build();

        this.authMissingHeaderCounter = meter
                .counterBuilder("auth_missing_header_total")
                .setDescription("Total number of requests with missing Authorization header")
                .setUnit("1")
                .build();

        this.authInvalidTokenCounter = meter
                .counterBuilder("auth_invalid_token_total")
                .setDescription("Total number of requests with invalid tokens")
                .setUnit("1")
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getURI().getPath();
        String method = request.getMethod().name();

        Span span = tracer.spanBuilder("jwt-authentication")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", method)
                .setAttribute("http.target", requestPath)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            final List<String> apiEndpoints = Arrays.asList("/auth/login", "/auth/register");

            logger.debug("Processing authentication for path: {}", requestPath);

            Predicate<ServerHttpRequest> isApiSecured = r -> apiEndpoints.stream()
                    .noneMatch(uri -> r.getURI().getPath().contains(uri));

            authRequestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("http.method"), method,
                    AttributeKey.stringKey("http.target"), requestPath));

            if (isApiSecured.test(exchange.getRequest())) {
                if (authMissing(request)) {
                    logger.warn("Missing Authorization header for path: {}", requestPath);
                    authMissingHeaderCounter.add(1, Attributes.of(
                            AttributeKey.stringKey("http.method"), method,
                            AttributeKey.stringKey("http.target"), requestPath));
                    span.setStatus(StatusCode.ERROR, "Missing Authorization header");
                    return onError(exchange, "Missing Authorization header");
                }

                String token = request.getHeaders().getOrEmpty("Authorization").get(0);

                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                    try {
                        jwtUtil.validateToken(token);

                        logger.info("Authentication successful for path: {}", requestPath);
                        authSuccessCounter.add(1, Attributes.of(
                                AttributeKey.stringKey("http.method"), method,
                                AttributeKey.stringKey("http.target"), requestPath));

                        span.setStatus(StatusCode.OK);

                        return chain.filter(exchange)
                                .doFinally(signalType -> recordMetrics(exchange, startTime, span, "success"));
                    } catch (Exception e) {
                        logger.error("Invalid token for path: {} - Error: {}", requestPath, e.getMessage());
                        authInvalidTokenCounter.add(1, Attributes.of(
                                AttributeKey.stringKey("http.method"), method,
                                AttributeKey.stringKey("http.target"), requestPath,
                                AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));
                        span.setStatus(StatusCode.ERROR, "Invalid token: " + e.getMessage());
                        span.recordException(e);
                        return onError(exchange, "Invalid token");
                    }
                } else {
                    logger.warn("Invalid Authorization header format for path: {}", requestPath);
                    authInvalidTokenCounter.add(1, Attributes.of(
                            AttributeKey.stringKey("http.method"), method,
                            AttributeKey.stringKey("http.target"), requestPath,
                            AttributeKey.stringKey("error.type"), "InvalidFormat"));
                    span.setStatus(StatusCode.ERROR, "Invalid Authorization header format");
                    return onError(exchange, "Invalid Authorization header format");
                }
            }

            span.setStatus(StatusCode.OK);
            return chain.filter(exchange)
                    .doFinally(signalType -> recordMetrics(exchange, startTime, span, "bypassed"));
        } finally {
            span.end();
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("error-message", message);
        return response.setComplete();
    }

    private boolean authMissing(ServerHttpRequest request) {
        return !request.getHeaders().containsKey("Authorization");
    }

    private void recordMetrics(ServerWebExchange exchange, long startTime, Span span, String status) {
        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

        span.setAttribute("auth.duration", durationSeconds);
        span.setAttribute("auth.status", status);

        Attributes attributes = Attributes.of(
                AttributeKey.stringKey("http.method"), exchange.getRequest().getMethod().name(),
                AttributeKey.stringKey("http.target"), exchange.getRequest().getURI().getPath(),
                AttributeKey.stringKey("auth.status"), status);

        if ("error".equals(status)) {
            authFailureCounter.add(1, attributes);
        }

        logger.debug("Authentication processed in {} seconds with status: {}", durationSeconds, status);
    }
}