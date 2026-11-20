package com.orderflow.order.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.EventType;
import com.orderflow.common.PaymentResultPayload;
import com.orderflow.order.domain.port.InventoryReservationPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentResultHandlerTest {

    @Mock
    private OrderTransaction tx;
    @Mock
    private InventoryReservationPort inventory;
    @Spy
    private EventSerde serde = new EventSerde();
    @InjectMocks
    private PaymentResultHandler handler;

    private final UUID orderId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @Test
    void confirmsOnAuthorization() {
        handler.handle(envelope(true));

        verify(tx).onPaymentAuthorized(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(eventId));
        verify(inventory, never()).release(orderId);
    }

    @Test
    void releasesStockWhenPaymentIsDeclined() {
        when(tx.beginCompensationIfNeeded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(eventId)))
                .thenReturn(true);

        handler.handle(envelope(false));

        verify(inventory).release(orderId);
        verify(tx).finishCompensation(orderId, eventId);
    }

    @Test
    void doesNotReleaseWhenTheDeclineWasAlreadyApplied() {
        when(tx.beginCompensationIfNeeded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(eventId)))
                .thenReturn(false);

        handler.handle(envelope(false));

        verify(inventory, never()).release(orderId);
    }

    private EventEnvelope envelope(boolean authorized) {
        PaymentResultPayload payload = new PaymentResultPayload(
                UUID.randomUUID(), orderId, authorized, "CARD", 1500, "USD", authorized ? null : "card declined");
        return new EventEnvelope(
                eventId,
                authorized ? EventType.PAYMENT_AUTHORIZED.name() : EventType.PAYMENT_DECLINED.name(),
                Instant.parse("2026-11-20T12:00:00Z"),
                "corr",
                orderId,
                1,
                serde.toNode(payload)
        );
    }
}
