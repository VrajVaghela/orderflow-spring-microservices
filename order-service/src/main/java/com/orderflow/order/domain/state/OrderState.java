package com.orderflow.order.domain.state;

import com.orderflow.order.domain.IllegalOrderTransitionException;
import com.orderflow.order.domain.OrderStatus;

/**
 * State pattern: each order status is a behavior object.
 * Legal transitions are methods; everything else fails closed.
 * Instances are stateless singletons. The data lives on {@code Order}.
 */
public interface OrderState {

    OrderStatus status();

    default OrderState requestPayment() {
        throw illegal("request payment");
    }

    default OrderState reject() {
        throw illegal("reject");
    }

    default OrderState confirm() {
        throw illegal("confirm");
    }

    default OrderState beginCompensation() {
        throw illegal("compensate");
    }

    default OrderState finishCancellation() {
        throw illegal("cancel");
    }

    private IllegalOrderTransitionException illegal(String action) {
        return new IllegalOrderTransitionException(status(), action);
    }
}
