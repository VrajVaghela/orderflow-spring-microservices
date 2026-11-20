package com.orderflow.common;

import java.util.List;
import java.util.UUID;

/**
 * Full read-model snapshot carried on every order event.
 * Consumers apply it only when {@code aggregateVersion} is newer, so redelivery
 * and reordering do not need a multi-document transaction.
 * {@code paymentToken} is populated only on {@code ORDER_PAYMENT_REQUESTED}.
 * It is a fake demo token, never a PAN, and the query model does not store it.
 */
public record OrderSnapshot(
        UUID orderId,
        String customerId,
        String status,
        String currency,
        long totalMinor,
        List<OrderLinePayload> lines,
        String paymentMethod,
        String paymentToken,
        String reservationId,
        String reason
) {
}
