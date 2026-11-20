package com.orderflow.order.application;

import java.util.List;

public record PlaceOrderCommand(
        String customerId,
        String currency,
        String paymentMethod,
        String paymentToken,
        List<Line> lines
) {

    public record Line(String sku, int quantity, long unitPriceMinor) {
    }
}
