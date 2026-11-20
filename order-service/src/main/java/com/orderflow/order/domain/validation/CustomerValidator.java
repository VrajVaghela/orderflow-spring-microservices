package com.orderflow.order.domain.validation;

import com.orderflow.order.application.PlaceOrderCommand;
import com.orderflow.order.domain.OrderValidationException;

public final class CustomerValidator extends ValidationHandler {

    private static final java.util.regex.Pattern CUSTOMER = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void check(PlaceOrderCommand command) {
        if (command.customerId() == null || !CUSTOMER.matcher(command.customerId()).matches()) {
            throw new OrderValidationException("customerId must be 1-64 characters of letters, digits, '_' or '-'");
        }
    }
}
