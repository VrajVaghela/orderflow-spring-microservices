package com.orderflow.order.infrastructure.grpc;

/**
 * Unchecked wrapper for a timed-out or otherwise failed inventory RPC attempt.
 * Resilience4j retries and counts this exception. Business declines are not wrapped.
 */
public class InventoryCallFailedException extends RuntimeException {

    public InventoryCallFailedException(Throwable cause) {
        super(cause);
    }
}
