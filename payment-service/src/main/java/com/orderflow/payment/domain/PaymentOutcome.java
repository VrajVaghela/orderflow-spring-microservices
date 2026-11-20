package com.orderflow.payment.domain;

public record PaymentOutcome(boolean authorized, String reason) {

    public static PaymentOutcome approve() {
        return new PaymentOutcome(true, null);
    }

    public static PaymentOutcome decline(String reason) {
        return new PaymentOutcome(false, reason);
    }
}
