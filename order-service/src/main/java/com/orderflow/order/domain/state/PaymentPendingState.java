package com.orderflow.order.domain.state;

import com.orderflow.order.domain.OrderStatus;

public final class PaymentPendingState implements OrderState {

    public static final PaymentPendingState INSTANCE = new PaymentPendingState();

    private PaymentPendingState() {
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.PAYMENT_PENDING;
    }

    @Override
    public OrderState confirm() {
        return ConfirmedState.INSTANCE;
    }

    @Override
    public OrderState beginCompensation() {
        return CompensatingState.INSTANCE;
    }
}
