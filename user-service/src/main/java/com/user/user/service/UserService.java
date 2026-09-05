package com.user.user.service;

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

import com.user.user.dto.UserMapper;
import com.user.user.dto.UserRequest;
import com.user.user.entity.User;
import com.user.user.enums.Role;
import com.user.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Service
public class UserService {
        private static final Logger logger = LoggerFactory.getLogger(UserService.class);

        private final UserRepository userRepository;
        private final UserMapper userMapper;
        private final PasswordEncoder passwordEncoder;

        private final Tracer tracer;
        private final Meter meter;

        private final LongCounter requestsTotal;
        private final DoubleHistogram requestsDurationSeconds;
        private final LongCounter failureTotal;

        public UserService(UserRepository userRepository,
                        UserMapper userMapper,
                        PasswordEncoder passwordEncoder,
                        OpenTelemetry openTelemetry) {
                this.userRepository = userRepository;
                this.userMapper = userMapper;
                this.passwordEncoder = passwordEncoder;
                this.tracer = openTelemetry.getTracer("user-service", "1.0.0");
                this.meter = openTelemetry.getMeter("user-service");

                this.requestsTotal = meter
                                .counterBuilder("requests_total")
                                .setDescription("Total number of user requests")
                                .setUnit("1")
                                .build();

                this.requestsDurationSeconds = meter
                                .histogramBuilder("requests_duration_seconds")
                                .setDescription("User request duration in seconds")
                                .setUnit("s")
                                .build();

                this.failureTotal = meter
                                .counterBuilder("failure_total")
                                .setDescription("Total number of failed user requests")
                                .setUnit("1")
                                .build();
        }

        public User createUser(UserRequest userRequest) throws Exception {
                long startTime = System.nanoTime();
                String method = "createUser";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("user.username", userRequest.getUsername())
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Creating user with username: {}", userRequest.getUsername());

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service"));

                        if (userRepository.findByUsername(userRequest.getUsername()).isPresent()) {
                                status = "error";
                                logger.error("User already exists: {}", userRequest.getUsername());
                                span.setStatus(StatusCode.ERROR, "User already exists");
                                throw new Exception("User already exists");
                        }

                        User user = userMapper.toEntity(userRequest);
                        user.setPassword(passwordEncoder.encode(user.getPassword()));
                        user.setRole(Role.USER);

                        User savedUser = userRepository.save(user);

                        span.setAttribute("user.id", savedUser.getId().toString());
                        span.setStatus(StatusCode.OK);

                        logger.info("User created successfully: userId={}, username={}",
                                        savedUser.getId(), savedUser.getUsername());

                        return savedUser;

                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);
                        logger.error("Failed to create user with username: {}", userRequest.getUsername(), e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "user-service");

                        requestsTotal.add(1, attributes);
                        requestsDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public User getUserByUsername(String username) throws Exception {
                long startTime = System.nanoTime();
                String method = "getUserByUsername";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("user.username", username)
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Retrieving user with username: {}", username);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service"));

                        User user = userRepository.findByUsername(username).orElse(null);
                        if (user == null) {
                                status = "error";
                                logger.error("User not found: {}", username);
                                span.setStatus(StatusCode.ERROR, "User not found");
                                throw new Exception("User not found");
                        }

                        span.setAttribute("user.id", user.getId().toString());
                        span.setStatus(StatusCode.OK);

                        logger.info("User retrieved successfully: userId={}, username={}",
                                        user.getId(), user.getUsername());

                        return user;

                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);
                        logger.error("Failed to retrieve user with username: {}", username, e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "user-service");

                        requestsTotal.add(1, attributes);
                        requestsDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public User getUserById(Long id) throws Exception {
                long startTime = System.nanoTime();
                String method = "getUserById";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("user.id", id)
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Retrieving user with id: {}", id);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service"));

                        User user = userRepository.findById(id).orElse(null);
                        if (user == null) {
                                status = "error";
                                logger.error("User not found: {}", id);
                                span.setStatus(StatusCode.ERROR, "User not found");
                                throw new Exception("User not found");
                        }

                        span.setAttribute("user.username", user.getUsername());
                        span.setStatus(StatusCode.OK);

                        logger.info("User retrieved successfully: userId={}, username={}",
                                        user.getId(), user.getUsername());

                        return user;

                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);
                        logger.error("Failed to retrieve user with id: {}", id, e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "user-service");

                        requestsTotal.add(1, attributes);
                        requestsDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public void deleteUser(Long id) throws Exception {
                long startTime = System.nanoTime();
                String method = "deleteUser";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("user.id", id)
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Deleting user with id: {}", id);

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service"));

                        User user = userRepository.findById(id).orElse(null);
                        if (user == null) {
                                status = "error";
                                logger.error("User not found: {}", id);
                                span.setStatus(StatusCode.ERROR, "User not found");
                                throw new Exception("User not found");
                        }

                        userRepository.delete(user);

                        span.setAttribute("user.username", user.getUsername());
                        span.setStatus(StatusCode.OK);

                        logger.info("User deleted successfully: userId={}, username={}",
                                        user.getId(), user.getUsername());

                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);
                        logger.error("Failed to delete user with id: {}", id, e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "user-service");

                        requestsTotal.add(1, attributes);
                        requestsDurationSeconds.record(durationSeconds, attributes);
                }
        }

        public List<User> getAllUsers() {
                long startTime = System.nanoTime();
                String method = "getAllUsers";
                String status = "success";

                Span span = tracer.spanBuilder(method)
                                .setSpanKind(SpanKind.SERVER)
                                .startSpan();

                try (Scope scope = span.makeCurrent()) {
                        logger.info("Retrieving all users");

                        requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service"));

                        List<User> users = userRepository.findAll();

                        span.setAttribute("users.count", users.size());
                        span.setStatus(StatusCode.OK);

                        logger.info("Retrieved {} users successfully", users.size());

                        return users;

                } catch (Exception e) {
                        status = "error";
                        span.setStatus(StatusCode.ERROR, e.getMessage());
                        span.recordException(e);
                        logger.error("Failed to retrieve all users", e);

                        failureTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("service"), "user-service",
                                        AttributeKey.stringKey("error.type"), e.getClass().getSimpleName()));

                        throw e;
                } finally {
                        span.end();

                        double durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;

                        Attributes attributes = Attributes.of(
                                        AttributeKey.stringKey("method"), method,
                                        AttributeKey.stringKey("status"), status,
                                        AttributeKey.stringKey("service"), "user-service");

                        requestsTotal.add(1, attributes);
                        requestsDurationSeconds.record(durationSeconds, attributes);
                }
        }
}