package com.orderflow.payment.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Factory: selects the strategy for a method. A missing strategy is a
 * programming error at wiring time, surfaced as a decline so the consumer
 * does not retry forever.
 */
@Component
public class PaymentStrategyFactory {

    private final Map<PaymentMethod, PaymentStrategy> strategies;

    public PaymentStrategyFactory(List<PaymentStrategy> strategies) {
        this.strategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentStrategy::method, Function.identity()));
    }

    public PaymentStrategy forMethod(PaymentMethod method) {
        PaymentStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new IllegalArgumentException("unsupported payment method " + method);
        }
        return strategy;
    }
}
