package com.orderflow.order.domain.event;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DomainEventPublisher {

    private final List<DomainEventObserver> observers;

    public DomainEventPublisher(List<DomainEventObserver> observers) {
        this.observers = List.copyOf(observers);
    }

    public void publishAll(List<OrderDomainEvent> events) {
        for (OrderDomainEvent event : events) {
            for (DomainEventObserver observer : observers) {
                observer.onEvent(event);
            }
        }
    }
}
