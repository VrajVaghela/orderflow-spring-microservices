package com.orderflow.order.domain.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.order.application.PlaceOrderCommand;
import com.orderflow.order.domain.OrderValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationChainTest {

    private final ValidationHandler chain = chain();

    @Test
    void acceptsACardOrder() {
        chain.handle(command("cus_1", "USD", "CARD", "tok_visa_4242", line("SKU-100", 1, 100)));
    }

    @Test
    void stopsAtTheFirstBrokenLink() {
        assertThatThrownBy(() -> chain.handle(command("bad id", "USD", "CARD", "tok_visa", line("SKU-100", 1, 100))))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    void requiresATokenForCards() {
        assertThatThrownBy(() -> chain.handle(command("cus_1", "USD", "CARD", null, line("SKU-100", 1, 100))))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("paymentToken");
    }

    @Test
    void allowsAWalletWithoutAToken() {
        chain.handle(command("cus_1", "EUR", "WALLET", null, line("SKU-200", 2, 2500)));
    }

    @Test
    void rejectsDuplicateSkusAndUnknownCurrency() {
        assertThatThrownBy(() -> chain.handle(new PlaceOrderCommand(
                "cus_1",
                "USD",
                "CARD",
                "tok_visa",
                List.of(line("SKU-100", 1, 100), line("SKU-100", 1, 100))
        ))).isInstanceOf(OrderValidationException.class).hasMessageContaining("duplicate");

        assertThatThrownBy(() -> chain.handle(command("cus_1", "GBP", "CARD", "tok_visa", line("SKU-100", 1, 100))))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("currency");
    }

    private static ValidationHandler chain() {
        ValidationHandler customer = new CustomerValidator();
        customer.linkWith(new CurrencyValidator())
                .linkWith(new LineItemsValidator())
                .linkWith(new PaymentValidator());
        return customer;
    }

    private static PlaceOrderCommand command(String customer, String currency, String method, String token, PlaceOrderCommand.Line line) {
        return new PlaceOrderCommand(customer, currency, method, token, List.of(line));
    }

    private static PlaceOrderCommand.Line line(String sku, int quantity, long price) {
        return new PlaceOrderCommand.Line(sku, quantity, price);
    }
}
