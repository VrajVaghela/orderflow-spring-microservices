package com.orderflow.order.domain;

import java.util.List;

public class OrderValidationException extends RuntimeException {

    private final List<String> errors;

    public OrderValidationException(String error) {
        this(List.of(error));
    }

    public OrderValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
