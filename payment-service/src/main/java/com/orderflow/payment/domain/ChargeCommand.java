package com.orderflow.payment.domain;

import java.util.UUID;

public record ChargeCommand(UUID orderId, long amountMinor, String currency, String token) {
}
