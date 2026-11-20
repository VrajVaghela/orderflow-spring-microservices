package com.orderflow.order.domain.state;

import com.orderflow.order.domain.OrderStatus;

public final class ConfirmedState implements OrderState {

    public static final ConfirmedState INSTANCE = new ConfirmedState();

    private ConfirmedState() {
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.CONFIRMED;
    }
}
