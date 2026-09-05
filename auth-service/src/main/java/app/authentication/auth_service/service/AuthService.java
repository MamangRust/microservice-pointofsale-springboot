package app.authentication.auth_service.service;

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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import app.authentication.auth_service.client.UserClient;
import app.authentication.auth_service.dto.AuthRequest;
import app.authentication.auth_service.dto.AuthResponse;
import app.authentication.auth_service.dto.UserDto;
import app.authentication.auth_service.exc.WrongCredentialsException;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserClient userClient;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final Tracer tracer;
    private final Meter meter;

    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;
    private final LongCounter failureTotal;

    public AuthService(UserClient userClient,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            OpenTelemetry openTelemetry) {
        this.userClient = userClient;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tracer = openTelemetry.getTracer("auth-service", "1.0.0");
        this.meter = openTelemetry.getMeter("auth-service");

        this.requestsTotal = meter
                .counterBuilder("requests_total")
                .setDescription("Total number of authentication requests")
                .setUnit("1")
                .build();

        this.requestDurationSeconds = meter
                .histogramBuilder("requests_duration_seconds")
                .setDescription("Authentication request duration in seconds")
                .setUnit("s")
                .build();

        this.failureTotal = meter
                .counterBuilder("failure_total")
                .setDescription("Total number of failed authentication attempts")
                .setUnit("1")
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        long startTime = System.nanoTime();
        String method = "login";
        String status = "success";

        Span span = tracer.spanBuilder(method)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("auth.username", request.getUsername())
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            logger.info("Service: Logging in user: {}", request.getUsername());

            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("method"), method,
                    AttributeKey.stringKey("service"), "auth-service"));

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (authentication.isAuthenticated()) {
                String token = jwtService.generateToken(request.getUsername());

                span.setAttribute("auth.success", true);
                span.setStatus(StatusCode.OK);

                logger.info("User logged in successfully: {}", request.getUsername());
                return AuthResponse.builder().token(token).build();
            }

            status = "failed";
            span.setAttribute("auth.success", false);
            span.setStatus(StatusCode.ERROR, "Authentication failed");
            throw new WrongCredentialsException("Invalid credentials");

        } catch (Exception e) {
            status = "error";
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);

            failureTotal.add(
                    1,
                    Attributes.of(
                            AttributeKey.stringKey("method"), method,
                            AttributeKey.stringKey("service"), "auth-service",
                            AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

            logger.error("Failed to login user: {}", request.getUsername(), e);
            throw e;
        } finally {
            span.end();

            double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

            Attributes attributes = Attributes.of(
                    AttributeKey.stringKey("method"), method,
                    AttributeKey.stringKey("status"), status,
                    AttributeKey.stringKey("service"), "auth-service");

            requestDurationSeconds.record(durationSeconds, attributes);
        }
    }

    public UserDto register(AuthRequest request) {
        long startTime = System.nanoTime();
        String method = "register";
        String status = "success";

        Span span = tracer.spanBuilder(method)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("auth.username", request.getUsername())
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            logger.info("Service: Registering user: {}", request.getUsername());

            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("method"), method,
                    AttributeKey.stringKey("service"), "auth-service"));

            UserDto user = userClient.createUser(request).getBody();

            if (user != null) {
                span.setAttribute("user.id", user.getId().toString());
                span.setAttribute("auth.success", true);
                span.setStatus(StatusCode.OK);

                logger.info("User registered successfully: userId={}, username={}",
                        user.getId(), request.getUsername());
                return user;
            }

            status = "failed";
            span.setAttribute("auth.success", false);
            span.setStatus(StatusCode.ERROR, "Registration failed");
            throw new RuntimeException("Failed to register user");

        } catch (Exception e) {
            status = "error";
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);

            failureTotal.add(
                    1,
                    Attributes.of(
                            AttributeKey.stringKey("method"), method,
                            AttributeKey.stringKey("service"), "auth-service",
                            AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

            logger.error("Failed to register user: {}", request.getUsername(), e);
            throw e;
        } finally {
            span.end();

            double durationSeconds = (System.nanoTime() - startTime) / 1_000_000.0;

            Attributes attributes = Attributes.of(
                    AttributeKey.stringKey("method"), method,
                    AttributeKey.stringKey("status"), status,
                    AttributeKey.stringKey("service"), "auth-service");

            requestDurationSeconds.record(durationSeconds, attributes);
        }
    }
}