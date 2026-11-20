package com.orderflow.order.infrastructure.observer;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.Topics;
import com.orderflow.order.domain.event.DomainEventObserver;
import com.orderflow.order.domain.event.OrderDomainEvent;
import com.orderflow.order.domain.port.OutboxStore;
import org.springframework.stereotype.Component;

@Component
public class OutboxRecordingObserver implements DomainEventObserver {

    private final OutboxStore outbox;
    private final EventSerde serde;

    public OutboxRecordingObserver(OutboxStore outbox, EventSerde serde) {
        this.outbox = outbox;
        this.serde = serde;
    }

    @Override
    public void onEvent(OrderDomainEvent event) {
        EventEnvelope envelope = new EventEnvelope(
                event.eventId(),
                event.type().name(),
                event.occurredAt(),
                event.correlationId(),
                event.aggregateId(),
                event.aggregateVersion(),
                serde.toNode(event.payload())
        );
        outbox.append(Topics.ORDER_EVENTS, envelope);
    }
}
