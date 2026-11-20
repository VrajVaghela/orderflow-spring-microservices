package com.orderflow.order.domain.validation;

import com.orderflow.order.application.PlaceOrderCommand;
import com.orderflow.order.domain.OrderValidationException;
import com.orderflow.order.domain.PaymentMethod;
import java.util.regex.Pattern;

public final class PaymentValidator extends ValidationHandler {

    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{4,64}$");

    @Override
    protected void check(PlaceOrderCommand command) {
        if (command.paymentMethod() == null) {
            throw new OrderValidationException("paymentMethod is required");
        }
        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(command.paymentMethod());
        } catch (IllegalArgumentException ex) {
            throw new OrderValidationException("paymentMethod must be CARD or WALLET");
        }
        String token = command.paymentToken();
        boolean present = token != null && !token.isBlank();
        if (method == PaymentMethod.CARD && !present) {
            throw new OrderValidationException("paymentToken is required for CARD");
        }
        if (present && !TOKEN.matcher(token).matches()) {
            throw new OrderValidationException("paymentToken must be 4-64 characters of letters, digits, '_' or '-'");
        }
    }
}
