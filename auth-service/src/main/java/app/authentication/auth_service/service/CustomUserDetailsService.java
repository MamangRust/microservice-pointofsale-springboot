package app.authentication.auth_service.service;

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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import app.authentication.auth_service.client.UserClient;
import app.authentication.auth_service.dto.UserDto;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserClient userClient;
    private final Tracer tracer;
    private final Meter meter;

    private final LongCounter requestsTotal;
    private final LongCounter requestDurationSeconds;
    private final LongCounter authFailureCounter;
    private final LongCounter userNotFoundCounter;

    public CustomUserDetailsService(UserClient userClient, OpenTelemetry openTelemetry) {
        this.userClient = userClient;
        this.tracer = openTelemetry.getTracer("custom-user-detail-service", "1.0.0");
        this.meter = openTelemetry.getMeter("custom-user-detail-service");

        this.requestsTotal = meter
                .counterBuilder("requests_total")
                .setDescription("Total number of authentication requests")
                .setUnit("1")
                .build();

        this.requestDurationSeconds = meter
                .counterBuilder("requests_duration_seconds")
                .setDescription("Total number of successful authentications")
                .setUnit("1")
                .build();

        this.authFailureCounter = meter
                .counterBuilder("auth_failure_total")
                .setDescription("Total number of failed authentications")
                .setUnit("1")
                .build();

        this.userNotFoundCounter = meter
                .counterBuilder("auth_user_not_found_total")
                .setDescription("Total number of authentication attempts with non-existent users")
                .setUnit("1")
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        long startTime = System.nanoTime();
        String method = "loadUserByUsername";
        String status = "success";

        Span span = tracer.spanBuilder(method)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("auth.username", username)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            logger.debug("Attempting to authenticate user: {}", username);

            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("auth.method"), method,
                    AttributeKey.stringKey("auth.service"), "auth-service"));

            UserDto user = userClient.getUserByUsername(username).getBody();

            if (user == null) {
                status = "user_not_found";
                logger.warn("User not found with username: {}", username);

                userNotFoundCounter.add(1, Attributes.of(
                        AttributeKey.stringKey("auth.method"), method,
                        AttributeKey.stringKey("auth.service"), "auth-service"));

                span.setStatus(StatusCode.ERROR, "User not found");
                throw new UsernameNotFoundException("User not found with username: " + username);
            }

            requestDurationSeconds.add(1, Attributes.of(
                    AttributeKey.stringKey("auth.method"), method,
                    AttributeKey.stringKey("auth.service"), "auth-service"));

            span.setAttribute("user.id", user.getId().toString());
            span.setStatus(StatusCode.OK);

            logger.info("User authenticated successfully: userId={}, username={}",
                    user.getId(), username);

            return new CustomUserDetails(user);

        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            status = "error";
            logger.error("Error authenticating user: {}", username, e);

            authFailureCounter.add(1, Attributes.of(
                    AttributeKey.stringKey("auth.method"), method,
                    AttributeKey.stringKey("auth.service"), "auth-service",
                    AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw new RuntimeException("Authentication error", e);
        } finally {
            span.end();

            double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

            Attributes attributes = Attributes.of(
                    AttributeKey.stringKey("method"), method,
                    AttributeKey.stringKey("status"), status,
                    AttributeKey.stringKey("service"), "auth-service");

            logger.debug("Authentication processed in {} seconds with status: {}", durationSeconds, status);
        }
    }
}