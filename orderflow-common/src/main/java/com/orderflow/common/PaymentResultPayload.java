package com.orderflow.common;

import java.util.UUID;

public record PaymentResultPayload(
        UUID paymentId,
        UUID orderId,
        boolean authorized,
        String method,
        long amountMinor,
        String currency,
        String reason
) {
}
