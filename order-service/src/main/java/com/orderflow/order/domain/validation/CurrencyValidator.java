package com.orderflow.order.domain.validation;

import com.orderflow.order.application.PlaceOrderCommand;
import com.orderflow.order.domain.CurrencyCode;
import com.orderflow.order.domain.OrderValidationException;

public final class CurrencyValidator extends ValidationHandler {

    @Override
    protected void check(PlaceOrderCommand command) {
        if (command.currency() == null) {
            throw new OrderValidationException("currency is required");
        }
        try {
            CurrencyCode.valueOf(command.currency());
        } catch (IllegalArgumentException ex) {
            throw new OrderValidationException("currency must be USD or EUR");
        }
    }
}
