package com.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event envelope for Kafka messages — mirrors Quarkus EventEnvelope pattern.
 *
 * @param <T> the payload type
 */
public record EventEnvelope<T>(
    String eventId,
    int schemaVersion,
    String eventType,
    Instant occurredAt,
    String domain,
    T payload
) {
    public static <T> EventEnvelope<T> withDefaults(T payload, String eventType) {
        return new EventEnvelope<>(
            UUID.randomUUID().toString(),
            1,
            eventType,
            Instant.now(),
            null,
            payload
        );
    }

    public static <T> EventEnvelope<T> withDefaults(T payload, String eventType, String domain) {
        return new EventEnvelope<>(
            UUID.randomUUID().toString(),
            1,
            eventType,
            Instant.now(),
            domain,
            payload
        );
    }
}