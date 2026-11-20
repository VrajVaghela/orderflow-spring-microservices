package com.orderflow.payment.domain;

/**
 * Strategy: each payment method is a different authorization algorithm.
 * The processor does not branch on the method.
 */
public interface PaymentStrategy {

    PaymentMethod method();

    PaymentOutcome charge(ChargeCommand command);
}
