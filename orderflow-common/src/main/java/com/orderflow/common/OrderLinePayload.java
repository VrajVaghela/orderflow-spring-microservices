package com.orderflow.common;

public record OrderLinePayload(String sku, int quantity, long unitPriceMinor) {
}
