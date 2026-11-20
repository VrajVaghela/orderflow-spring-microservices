package com.orderflow.query.api;

import com.orderflow.query.model.OrderViewDocument;
import java.util.List;

public record OrderView(
        String id,
        String customerId,
        String status,
        String currency,
        int totalMinor,
        String paymentMethod,
        String reservationId,
        String reason,
        int version,
        List<OrderLineView> lines
) {

    public record OrderLineView(String sku, int quantity, int unitPriceMinor) {
    }

    public static OrderView from(OrderViewDocument document) {
        List<OrderLineView> lines = document.getLines().stream()
                .map(line -> new OrderLineView(line.getSku(), line.getQuantity(), (int) line.getUnitPriceMinor()))
                .toList();
        return new OrderView(
                document.getId(),
                document.getCustomerId(),
                document.getStatus(),
                document.getCurrency(),
                (int) document.getTotalMinor(),
                document.getPaymentMethod(),
                document.getReservationId(),
                document.getReason(),
                (int) document.getVersion(),
                lines
        );
    }
}
