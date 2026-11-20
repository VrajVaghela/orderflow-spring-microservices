package com.orderflow.payment.api;

import com.orderflow.payment.infrastructure.persistence.PaymentEntity;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        String method,
        String status,
        long amountMinor,
        String currency,
        String reason,
        Instant createdAt
) {

    public static PaymentResponse from(PaymentEntity entity) {
        return new PaymentResponse(
                entity.getId(),
                entity.getOrderId(),
                entity.getMethod(),
                entity.getStatus().name(),
                entity.getAmountMinor(),
                entity.getCurrency(),
                entity.getReason(),
                entity.getCreatedAt()
        );
    }
}
