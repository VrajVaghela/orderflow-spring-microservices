package com.orderflow.payment.domain;

import org.springframework.stereotype.Component;

@Component
public class CardPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public PaymentOutcome charge(ChargeCommand command) {
        if (command.token() == null || command.token().isBlank()) {
            return PaymentOutcome.decline("card token is required");
        }
        if (command.token().endsWith("0002")) {
            return PaymentOutcome.decline("card declined");
        }
        return PaymentOutcome.approve();
    }
}
