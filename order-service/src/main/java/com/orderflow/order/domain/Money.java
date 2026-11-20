package com.orderflow.order.domain;

public record Money(long amountMinor, CurrencyCode currency) {

    public Money {
        if (amountMinor < 0) {
            throw new IllegalArgumentException("money cannot be negative");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency is required");
        }
    }

    public static Money zero(CurrencyCode currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException("currency mismatch");
        }
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        return new Money(Math.multiplyExact(amountMinor, quantity), currency);
    }
}
