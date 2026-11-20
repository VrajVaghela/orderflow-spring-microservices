package com.orderflow.common;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String type,
        Instant occurredAt,
        String correlationId,
        UUID aggregateId,
        long aggregateVersion,
        JsonNode payload
) {
}
