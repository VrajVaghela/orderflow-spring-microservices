package com.orderflow.order.domain.port;

import com.orderflow.common.EventEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {

    void append(String topic, EventEnvelope envelope);

    List<OutboxMessage> unpublished(int limit);

    void markPublished(UUID id, Instant publishedAt);

    void recordFailure(UUID id, String error, int attempts, boolean dead, Instant publishedAt);
}
