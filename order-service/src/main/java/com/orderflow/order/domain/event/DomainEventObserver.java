package com.orderflow.order.domain.event;

/**
 * Observer. The aggregate does not know about the outbox or metrics.
 * Both subscribe and are notified in the same transaction.
 */
public interface DomainEventObserver {

    void onEvent(OrderDomainEvent event);
}
