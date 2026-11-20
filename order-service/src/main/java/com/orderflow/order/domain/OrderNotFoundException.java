package com.orderflow.order.domain;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("order " + orderId + " was not found");
    }
}
