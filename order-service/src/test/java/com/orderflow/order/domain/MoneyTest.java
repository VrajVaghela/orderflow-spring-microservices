package com.orderflow.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsAndMultipliesInMinorUnits() {
        Money price = new Money(1999, CurrencyCode.USD);
        assertThat(price.times(2).plus(new Money(1, CurrencyCode.USD)).amountMinor()).isEqualTo(3999);
    }

    @Test
    void rejectsACurrencyMismatch() {
        assertThatThrownBy(() -> new Money(1, CurrencyCode.USD).plus(new Money(1, CurrencyCode.EUR)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
