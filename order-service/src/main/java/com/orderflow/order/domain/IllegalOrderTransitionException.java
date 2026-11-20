package com.orderflow.order.domain;

public class IllegalOrderTransitionException extends RuntimeException {

    public IllegalOrderTransitionException(OrderStatus status, String action) {
        super("cannot " + action + " while order is " + status);
    }
}
