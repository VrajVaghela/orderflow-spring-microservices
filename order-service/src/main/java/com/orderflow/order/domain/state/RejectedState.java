package com.orderflow.order.domain.state;

import com.orderflow.order.domain.OrderStatus;

public final class RejectedState implements OrderState {

    public static final RejectedState INSTANCE = new RejectedState();

    private RejectedState() {
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.REJECTED;
    }
}
