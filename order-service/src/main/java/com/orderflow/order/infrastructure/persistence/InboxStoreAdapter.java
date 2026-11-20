package com.orderflow.order.infrastructure.persistence;

import com.orderflow.order.domain.port.InboxStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InboxStoreAdapter implements InboxStore {

    private final InboxJpaRepository jpa;

    public InboxStoreAdapter(InboxJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean alreadyProcessed(UUID eventId) {
        return jpa.existsById(eventId);
    }

    @Override
    @Transactional
    public void markProcessed(UUID eventId, Instant processedAt) {
        jpa.insertIgnore(eventId, processedAt);
    }
}
