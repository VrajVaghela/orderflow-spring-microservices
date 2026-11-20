package com.orderflow.order.application;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.EventType;
import com.orderflow.common.PaymentResultPayload;
import com.orderflow.order.domain.port.InventoryReservationPort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentResultHandler {

    private final OrderTransaction tx;
    private final InventoryReservationPort inventory;
    private final EventSerde serde;

    public PaymentResultHandler(OrderTransaction tx, InventoryReservationPort inventory, EventSerde serde) {
        this.tx = tx;
        this.inventory = inventory;
        this.serde = serde;
    }

    public void handle(EventEnvelope envelope) {
        EventType type;
        try {
            type = EventType.valueOf(envelope.type());
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (type != EventType.PAYMENT_AUTHORIZED && type != EventType.PAYMENT_DECLINED) {
            return;
        }
        PaymentResultPayload result = serde.convert(envelope.payload(), PaymentResultPayload.class);
        if (type == EventType.PAYMENT_AUTHORIZED) {
            tx.onPaymentAuthorized(result, envelope.eventId());
            return;
        }
        if (tx.beginCompensationIfNeeded(result, envelope.eventId())) {
            releaseAndFinish(result.orderId(), envelope.eventId());
        }
    }

    public void releaseAndFinish(UUID orderId, UUID eventId) {
        inventory.release(orderId);
        tx.finishCompensation(orderId, eventId);
    }
}
