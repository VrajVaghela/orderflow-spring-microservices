package com.orderflow.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentStrategyTest {

    private final PaymentStrategyFactory factory = new PaymentStrategyFactory(
            List.of(new CardPaymentStrategy(), new WalletPaymentStrategy()));

    @Test
    void cardApprovesANormalTokenAndDeclinesTheTestSuffix() {
        assertThat(factory.forMethod(PaymentMethod.CARD).charge(charge("tok_visa_4242", 100)).authorized()).isTrue();
        PaymentOutcome declined = factory.forMethod(PaymentMethod.CARD).charge(charge("tok_decline_0002", 100));
        assertThat(declined.authorized()).isFalse();
        assertThat(declined.reason()).isEqualTo("card declined");
    }

    @Test
    void walletDeclinesAboveTheSimulatedBalance() {
        assertThat(factory.forMethod(PaymentMethod.WALLET).charge(charge(null, 50_000)).authorized()).isTrue();
        assertThat(factory.forMethod(PaymentMethod.WALLET).charge(charge(null, 50_001)).reason())
                .isEqualTo("insufficient wallet balance");
    }

    @Test
    void factoryRejectsAnUnwiredMethod() {
        PaymentStrategyFactory empty = new PaymentStrategyFactory(List.of());
        assertThatThrownBy(() -> empty.forMethod(PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ChargeCommand charge(String token, long amount) {
        return new ChargeCommand(UUID.randomUUID(), amount, "USD", token);
    }
}
