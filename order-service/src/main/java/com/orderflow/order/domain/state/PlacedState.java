package com.orderflow.order.domain.state;

import com.orderflow.order.domain.OrderStatus;

public final class PlacedState implements OrderState {

    public static final PlacedState INSTANCE = new PlacedState();

    private PlacedState() {
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.PLACED;
    }

    @Override
    public OrderState requestPayment() {
        return PaymentPendingState.INSTANCE;
    }

    @Override
    public OrderState reject() {
        return RejectedState.INSTANCE;
    }
}
