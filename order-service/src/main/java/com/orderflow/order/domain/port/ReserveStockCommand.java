package com.orderflow.order.domain.port;

import java.util.List;
import java.util.UUID;

public record ReserveStockCommand(UUID orderId, List<Line> lines) {

    public record Line(String sku, int quantity) {
    }
}
