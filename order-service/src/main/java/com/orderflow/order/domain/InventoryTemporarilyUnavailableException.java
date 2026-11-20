package com.orderflow.order.domain;

public class InventoryTemporarilyUnavailableException extends RuntimeException {

    public InventoryTemporarilyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
