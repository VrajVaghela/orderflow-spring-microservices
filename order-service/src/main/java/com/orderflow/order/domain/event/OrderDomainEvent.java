package com.orderflow.order.domain.event;

import com.orderflow.common.EventType;
import com.orderflow.common.OrderSnapshot;
import java.time.Instant;
import java.util.UUID;

public record OrderDomainEvent(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String correlationId,
        EventType type,
        OrderSnapshot payload
) {
}
