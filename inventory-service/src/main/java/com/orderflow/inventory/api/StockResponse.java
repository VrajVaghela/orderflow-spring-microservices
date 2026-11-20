package com.orderflow.inventory.api;

public record StockResponse(String sku, int available, int reserved) {
}
