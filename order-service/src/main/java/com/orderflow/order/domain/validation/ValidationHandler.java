package com.orderflow.order.domain.validation;

import com.orderflow.order.application.PlaceOrderCommand;

/**
 * Chain of Responsibility for request policy.
 * The builder still enforces structural invariants; this chain enforces
 * rules that can change without touching the aggregate.
 */
public abstract class ValidationHandler {

    private ValidationHandler next;

    public final ValidationHandler linkWith(ValidationHandler next) {
        this.next = next;
        return next;
    }

    public final void handle(PlaceOrderCommand command) {
        check(command);
        if (next != null) {
            next.handle(command);
        }
    }

    protected abstract void check(PlaceOrderCommand command);
}
