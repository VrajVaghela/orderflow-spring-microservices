package com.orderflow.order.domain.port;

import java.time.Instant;
import java.util.UUID;

public interface InboxStore {

    boolean alreadyProcessed(UUID eventId);

    void markProcessed(UUID eventId, Instant processedAt);
}
