package com.orderflow.order.domain.port;

import java.util.UUID;

public record IdempotencyRecord(String key, UUID orderId, String requestHash) {
}
