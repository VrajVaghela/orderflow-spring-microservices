package com.orderflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.order.domain.CurrencyCode;
import com.orderflow.order.domain.IdempotencyConflictException;
import com.orderflow.order.domain.InventoryTemporarilyUnavailableException;
import com.orderflow.order.domain.Money;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderLine;
import com.orderflow.order.domain.PaymentMethod;
import com.orderflow.order.domain.port.IdempotencyRecord;
import com.orderflow.order.domain.port.IdempotencyStore;
import com.orderflow.order.domain.port.InventoryReservationPort;
import com.orderflow.order.domain.port.OrderRepository;
import com.orderflow.order.domain.port.ReservationAttempt;
import com.orderflow.order.domain.state.OrderStateFactory;
import com.orderflow.order.domain.validation.CurrencyValidator;
import com.orderflow.order.domain.validation.CustomerValidator;
import com.orderflow.order.domain.validation.LineItemsValidator;
import com.orderflow.order.domain.validation.PaymentValidator;
import com.orderflow.order.domain.validation.ValidationHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

    @Mock
    private OrderTransaction tx;
    @Mock
    private InventoryReservationPort inventory;
    @Mock
    private IdempotencyStore idempotency;
    @Mock
    private OrderRepository orders;

    private final UUID orderId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        ValidationHandler chain = new CustomerValidator();
        chain.linkWith(new CurrencyValidator()).linkWith(new LineItemsValidator()).linkWith(new PaymentValidator());
        service = new PlaceOrderService(chain, tx, inventory, idempotency, orders, () -> orderId);
    }

    @Test
    void reservesStockAndReturnsTheUpdatedOrder() {
        Order placed = sample();
        Order pending = sample();
        pending.markPaymentPending("rsv-1", Instant.parse("2026-11-20T12:00:01Z"));
        when(idempotency.find("idem-key-1")).thenReturn(Optional.empty());
        when(tx.create(eq(orderId), any(), any(), eq("idem-key-1"), any())).thenReturn(placed);
        when(inventory.reserve(any())).thenReturn(ReservationAttempt.reserved("rsv-1"));
        when(tx.applyReservation(eq(orderId), any())).thenReturn(pending);

        OrderResponse response = service.place("idem-key-1", command());

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("PAYMENT_PENDING");
        verify(inventory).reserve(any());
    }

    @Test
    void leavesTheOrderPlacedWhenInventoryCannotBeReached() {
        Order placed = sample();
        when(tx.create(eq(orderId), any(), any(), eq(null), any())).thenReturn(placed);
        when(inventory.reserve(any())).thenThrow(new InventoryTemporarilyUnavailableException("down", new RuntimeException("down")));

        OrderResponse response = service.place(null, command());

        assertThat(response.status()).isEqualTo("PLACED");
        verify(tx, never()).applyReservation(any(), any());
    }

    @Test
    void replaysAnIdempotentKeyWithoutReservingAgain() {
        PlaceOrderCommand command = command();
        when(idempotency.find("idem-key-1")).thenReturn(Optional.of(
                new IdempotencyRecord("idem-key-1", orderId, PlaceOrderService.hash(command))));
        when(orders.findById(orderId)).thenReturn(Optional.of(sample()));

        OrderResponse response = service.place("idem-key-1", command);

        assertThat(response.orderId()).isEqualTo(orderId);
        verify(tx, never()).create(any(), any(), any(), any(), any());
        verify(inventory, never()).reserve(any());
    }

    @Test
    void rejectsAReusedKeyWithADifferentBody() {
        when(idempotency.find("idem-key-1")).thenReturn(Optional.of(
                new IdempotencyRecord("idem-key-1", orderId, "different")));

        assertThatThrownBy(() -> service.place("idem-key-1", command()))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    private Order sample() {
        return Order.builder()
                .id(orderId)
                .customerId("cus_1")
                .currency(CurrencyCode.USD)
                .paymentMethod(PaymentMethod.CARD)
                .paymentToken("tok_visa_4242")
                .lines(List.of(new OrderLine("SKU-100", 1, new Money(1500, CurrencyCode.USD))))
                .now(Instant.parse("2026-11-20T12:00:00Z"))
                .correlationId("corr")
                .states(new OrderStateFactory())
                .build();
    }

    private static PlaceOrderCommand command() {
        return new PlaceOrderCommand("cus_1", "USD", "CARD", "tok_visa_4242",
                List.of(new PlaceOrderCommand.Line("SKU-100", 1, 1500)));
    }
}
