package com.orderflow.order.domain;

public enum OrderStatus {
    PLACED,
    PAYMENT_PENDING,
    COMPENSATING,
    CONFIRMED,
    CANCELLED,
    REJECTED;

    public boolean terminal() {
        return this == CONFIRMED || this == CANCELLED || this == REJECTED;
    }
}
