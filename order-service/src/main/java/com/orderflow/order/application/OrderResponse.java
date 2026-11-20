package com.orderflow.order.application;

import com.orderflow.order.domain.Order;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String customerId,
        String status,
        String currency,
        long totalMinor,
        String paymentMethod,
        String reservationId,
        String reason,
        List<Line> lines
) {

    public record Line(String sku, int quantity, long unitPriceMinor) {
    }

    public static OrderResponse from(Order order) {
        List<Line> lines = order.lines().stream()
                .map(line -> new Line(line.sku(), line.quantity(), line.unitPrice().amountMinor()))
                .toList();
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.status().name(),
                order.currency().name(),
                order.total().amountMinor(),
                order.paymentMethod().name(),
                order.reservationId(),
                order.failureReason(),
                lines
        );
    }
}
