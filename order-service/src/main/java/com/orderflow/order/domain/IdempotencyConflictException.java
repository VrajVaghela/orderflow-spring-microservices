package com.orderflow.order.domain;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("this Idempotency-Key was already used with a different request");
    }
}
