package com.orderflow.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orderflow.inventory")
public record InventoryClientProperties(String host, int port, Duration deadline) {
}
