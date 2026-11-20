package com.orderflow.order.infrastructure.observer;

import com.orderflow.order.domain.event.DomainEventObserver;
import com.orderflow.order.domain.event.OrderDomainEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetricsObserver implements DomainEventObserver {

    private final MeterRegistry registry;

    public OrderMetricsObserver(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onEvent(OrderDomainEvent event) {
        registry.counter("orderflow.order.events", "type", event.type().name()).increment();
    }
}
