package com.orderflow.order.domain.port;

import java.util.UUID;

public record OutboxMessage(
        UUID id,
        String topic,
        UUID aggregateId,
        String payload,
        String correlationId,
        int attempts
) {
}
