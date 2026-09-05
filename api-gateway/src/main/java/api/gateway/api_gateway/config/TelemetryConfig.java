package api.gateway.api_gateway.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging.OpenTelemetryAppenderAutoConfiguration;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;

@Import(OpenTelemetryAppenderAutoConfiguration.class)
@Configuration
public class TelemetryConfig {

        @Value("${otel.exporter.otlp.endpoint:http://otel-collector:4317}")
        private String otlpEndpoint;

        @Value("${spring.application.name:apigateway}")
        private String serviceName;

        @Value("${otel.service.version:1.0.0}")
        private String serviceVersion;

        @Value("${otel.jfr.enabled:true}")
        private boolean jfrEnabled;

        private OpenTelemetry openTelemetry;
        private SdkTracerProvider tracerProvider;
        private SdkMeterProvider meterProvider;
        private SdkLoggerProvider loggerProvider;
        private RuntimeMetrics runtimeMetrics;

        @PostConstruct
        public void initialize() {
                Resource resource = Resource.getDefault()
                                .merge(Resource.create(Attributes.of(
                                                AttributeKey.stringKey("service.name"), serviceName,
                                                AttributeKey.stringKey("service.version"), serviceVersion,
                                                AttributeKey.stringKey("deployment.environment"), "production")));

                tracerProvider = SdkTracerProvider.builder()
                                .addSpanProcessor(BatchSpanProcessor.builder(
                                                OtlpGrpcSpanExporter.builder()
                                                                .setEndpoint(otlpEndpoint)
                                                                .setTimeout(Duration.ofSeconds(10))
                                                                .build())
                                                .setScheduleDelay(Duration.ofMillis(100))
                                                .build())
                                .setResource(resource)
                                .build();

                meterProvider = SdkMeterProvider.builder()
                                .registerMetricReader(PeriodicMetricReader.builder(
                                                OtlpGrpcMetricExporter.builder()
                                                                .setEndpoint(otlpEndpoint)
                                                                .setTimeout(Duration.ofSeconds(10))
                                                                .build())
                                                .setInterval(Duration.ofSeconds(30))
                                                .build())
                                .setResource(resource)
                                .build();

                loggerProvider = SdkLoggerProvider.builder()
                                .addLogRecordProcessor(BatchLogRecordProcessor.builder(
                                                OtlpGrpcLogRecordExporter.builder()
                                                                .setEndpoint(otlpEndpoint)
                                                                .setTimeout(Duration.ofSeconds(10))
                                                                .build())
                                                .setScheduleDelay(Duration.ofMillis(100))
                                                .build())
                                .setResource(resource)
                                .build();

                openTelemetry = OpenTelemetrySdk.builder()
                                .setTracerProvider(tracerProvider)
                                .setMeterProvider(meterProvider)
                                .setLoggerProvider(loggerProvider)
                                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                                .buildAndRegisterGlobal();

                OpenTelemetryAppender.install(openTelemetry);

                if (jfrEnabled) {
                        registerJvmMetrics();
                }
        }

        private void registerJvmMetrics() {
                try {
                        runtimeMetrics = RuntimeMetrics.builder(openTelemetry)
                                        .enableFeature(JfrFeature.MEMORY_POOL_METRICS)
                                        .enableFeature(JfrFeature.GC_DURATION_METRICS)
                                        .enableFeature(JfrFeature.CPU_UTILIZATION_METRICS)
                                        .enableFeature(JfrFeature.THREAD_METRICS)
                                        .enableFeature(JfrFeature.CLASS_LOAD_METRICS)
                                        .enableFeature(JfrFeature.BUFFER_METRICS)
                                        .build();

                        System.out.println("✅ JVM Runtime Metrics (JFR) initialized successfully");
                        System.out.println(
                                        "📊 Enabled features: MEMORY_POOL, GC_DURATION, CPU_UTILIZATION, THREAD, CLASS_LOAD, BUFFER");
                        System.out.println(
                                        "📊 Default features: CONTEXT_SWITCH, CPU_COUNT, LOCK, MEMORY_ALLOCATION, NETWORK_IO");

                } catch (Exception e) {
                        System.err.println("⚠️ Failed to initialize JFR metrics: " + e.getMessage());
                        System.err.println("   This is expected if running on Java < 17 or GraalVM Community Edition");
                }
        }

        @PreDestroy
        public void shutdown() {
                if (runtimeMetrics != null) {
                        try {
                                runtimeMetrics.close();
                                System.out.println("✅ JVM Runtime Metrics (JFR) closed");
                        } catch (Exception e) {
                                System.err.println("⚠️ Error closing JFR metrics: " + e.getMessage());
                        }
                }

                if (tracerProvider != null) {
                        tracerProvider.close();
                }
                if (meterProvider != null) {
                        meterProvider.close();
                }
                if (loggerProvider != null) {
                        loggerProvider.close();
                }
        }

        @Bean
        public OpenTelemetry openTelemetry() {
                return openTelemetry;
        }

        @Bean
        public Tracer tracer() {
                return openTelemetry.getTracer(serviceName, serviceVersion);
        }

        @Bean
        public TextMapPropagator textMapPropagator() {
                return W3CTraceContextPropagator.getInstance();
        }
}