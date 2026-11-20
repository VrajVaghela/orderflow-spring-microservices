package com.orderflow.order.domain.state;

import com.orderflow.order.domain.OrderStatus;

public final class CancelledState implements OrderState {

    public static final CancelledState INSTANCE = new CancelledState();

    private CancelledState() {
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.CANCELLED;
    }
}
