package com.orderflow.order.infrastructure.persistence;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.order.domain.port.OutboxMessage;
import com.orderflow.order.domain.port.OutboxStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxStoreAdapter implements OutboxStore {

    private final OutboxJpaRepository jpa;
    private final EventSerde serde;

    public OutboxStoreAdapter(OutboxJpaRepository jpa, EventSerde serde) {
        this.jpa = jpa;
        this.serde = serde;
    }

    @Override
    public void append(String topic, EventEnvelope envelope) {
        OutboxEntity entity = new OutboxEntity();
        entity.setId(envelope.eventId());
        entity.setAggregateId(envelope.aggregateId());
        entity.setAggregateType("Order");
        entity.setEventType(envelope.type());
        entity.setTopic(topic);
        entity.setPayload(serde.write(envelope));
        entity.setCorrelationId(envelope.correlationId());
        entity.setCreatedAt(envelope.occurredAt());
        entity.setAttempts(0);
        jpa.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxMessage> unpublished(int limit) {
        return jpa.findUnpublished(PageRequest.of(0, limit)).stream()
                .map(entity -> new OutboxMessage(
                        entity.getId(),
                        entity.getTopic(),
                        entity.getAggregateId(),
                        entity.getPayload(),
                        entity.getCorrelationId(),
                        entity.getAttempts()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID id, Instant publishedAt) {
        jpa.markPublished(id, publishedAt);
    }

    @Override
    @Transactional
    public void recordFailure(UUID id, String error, int attempts, boolean dead, Instant publishedAt) {
        String trimmed = error == null ? null : error.substring(0, Math.min(error.length(), 512));
        jpa.recordFailure(id, trimmed, attempts, dead ? publishedAt : null);
    }
}
