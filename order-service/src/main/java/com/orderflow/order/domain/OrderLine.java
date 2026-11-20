package com.orderflow.order.domain;

public record OrderLine(String sku, int quantity, Money unitPrice) {

    public OrderLine {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("unit price is required");
        }
    }

    public Money lineTotal() {
        return unitPrice.times(quantity);
    }
}
