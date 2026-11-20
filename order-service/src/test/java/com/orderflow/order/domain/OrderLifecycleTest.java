package com.orderflow.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.common.EventType;
import com.orderflow.order.domain.event.OrderDomainEvent;
import com.orderflow.order.domain.state.OrderStateFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderLifecycleTest {

    private final OrderStateFactory states = new OrderStateFactory();
    private final Instant now = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    void builderTotalsLinesAndRecordsPlacementWithoutTheToken() {
        Order order = newOrder();

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.total().amountMinor()).isEqualTo(3998);
        assertThat(order.aggregateVersion()).isEqualTo(1);
        OrderDomainEvent placed = order.pullEvents().get(0);
        assertThat(placed.type()).isEqualTo(EventType.ORDER_PLACED);
        assertThat(placed.payload().paymentToken()).isNull();
        assertThat(placed.payload().lines()).hasSize(1);
    }

    @Test
    void happyPathEndsConfirmed() {
        Order order = newOrder();
        order.pullEvents();
        order.markPaymentPending("rsv-1", now.plusSeconds(1));
        OrderDomainEvent requested = order.pullEvents().get(0);
        order.confirm(now.plusSeconds(2));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(requested.type()).isEqualTo(EventType.ORDER_PAYMENT_REQUESTED);
        assertThat(requested.payload().paymentToken()).isEqualTo("tok_visa_4242");
        assertThat(requested.payload().reservationId()).isEqualTo("rsv-1");
        assertThat(order.pullEvents().get(0).type()).isEqualTo(EventType.ORDER_CONFIRMED);
        assertThat(order.pullEvents()).isEmpty();
    }

    @Test
    void paymentFailureCancelsAfterCompensation() {
        Order order = newOrder();
        order.pullEvents();
        order.markPaymentPending("rsv-1", now);
        order.pullEvents();
        order.beginCompensation("card declined", now.plusSeconds(1));
        order.finishCancellation(now.plusSeconds(2));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.failureReason()).isEqualTo("card declined");
        assertThat(order.pullEvents().get(0).type()).isEqualTo(EventType.ORDER_CANCELLED);
    }

    @Test
    void stockRejectionIsTerminal() {
        Order order = newOrder();
        order.pullEvents();
        order.reject("insufficient stock for SKU-LOW", now);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThatThrownBy(() -> order.confirm(now))
                .isInstanceOf(IllegalOrderTransitionException.class);
    }

    @Test
    void confirmedOrderCannotChange() {
        Order order = newOrder();
        order.pullEvents();
        order.markPaymentPending("rsv-1", now);
        order.confirm(now);
        assertThatThrownBy(() -> order.beginCompensation("late", now))
                .isInstanceOf(IllegalOrderTransitionException.class);
    }

    @Test
    void factoryRebuildsEveryStatus() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(states.from(status).status()).isEqualTo(status);
        }
    }

    private Order newOrder() {
        return Order.builder()
                .id(UUID.randomUUID())
                .customerId("cus_1")
                .currency(CurrencyCode.USD)
                .paymentMethod(PaymentMethod.CARD)
                .paymentToken("tok_visa_4242")
                .lines(List.of(new OrderLine("SKU-100", 2, new Money(1999, CurrencyCode.USD))))
                .now(now)
                .correlationId("corr-1")
                .states(states)
                .build();
    }
}
