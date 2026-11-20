package com.orderflow.inventory.application;

import java.util.List;

public record ReserveCommand(String orderId, List<Line> lines) {

    public record Line(String sku, int quantity) {
    }
}
