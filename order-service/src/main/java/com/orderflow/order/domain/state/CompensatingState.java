package com.orderflow.order.domain.state;

import com.orderflow.order.domain.OrderStatus;

public final class CompensatingState implements OrderState {

    public static final CompensatingState INSTANCE = new CompensatingState();

    private CompensatingState() {
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.COMPENSATING;
    }

    @Override
    public OrderState finishCancellation() {
        return CancelledState.INSTANCE;
    }
}
