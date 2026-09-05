package com.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void withDefaults_twoArgFactory_fillsStandardFields() {
        String payload = "order-created-payload";

        EventEnvelope<String> envelope = EventEnvelope.withDefaults(payload, "OrderCreated");

        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.eventType()).isEqualTo("OrderCreated");
        assertThat(envelope.payload()).isSameAs(payload);
        assertThat(envelope.domain()).isNull();
        assertThatCode(() -> java.util.UUID.fromString(envelope.eventId()))
                .doesNotThrowAnyException();
        assertThat(envelope.occurredAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void withDefaults_threeArgFactory_setsDomain() {
        EventEnvelope<String> envelope =
                EventEnvelope.withDefaults("payload", "OrderCreated", "order");

        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.eventType()).isEqualTo("OrderCreated");
        assertThat(envelope.domain()).isEqualTo("order");
        assertThat(envelope.payload()).isEqualTo("payload");
    }

    @Test
    void withDefaults_generatesDistinctEventIdsPerCall() {
        EventEnvelope<String> first = EventEnvelope.withDefaults("p", "OrderCreated", "order");
        EventEnvelope<String> second = EventEnvelope.withDefaults("p", "OrderCreated", "order");

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    @Test
    void withDefaults_supportsComplexPayloadType() {
        record OrderPayload(String orderId, long total) {}

        OrderPayload payload = new OrderPayload("ORD-1", 25_000L);

        EventEnvelope<OrderPayload> envelope =
                EventEnvelope.withDefaults(payload, "OrderCreated", "order");

        assertThat(envelope.payload()).isEqualTo(payload);
    }
}
