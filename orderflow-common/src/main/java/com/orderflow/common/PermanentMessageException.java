package com.orderflow.common;

/**
 * A Kafka record that will never succeed. Listeners swallow it so the
 * partition is not blocked; transient failures are left to propagate.
 */
public class PermanentMessageException extends RuntimeException {

    public PermanentMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
