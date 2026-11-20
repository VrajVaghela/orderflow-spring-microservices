package com.orderflow.payment.domain;

import org.springframework.stereotype.Component;

@Component
public class WalletPaymentStrategy implements PaymentStrategy {

    public static final long WALLET_LIMIT_MINOR = 50_000L;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.WALLET;
    }

    @Override
    public PaymentOutcome charge(ChargeCommand command) {
        if (command.amountMinor() > WALLET_LIMIT_MINOR) {
            return PaymentOutcome.decline("insufficient wallet balance");
        }
        return PaymentOutcome.approve();
    }
}
