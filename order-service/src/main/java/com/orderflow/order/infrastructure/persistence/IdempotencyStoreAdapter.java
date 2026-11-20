package com.orderflow.order.infrastructure.persistence;

import com.orderflow.order.domain.port.IdempotencyRecord;
import com.orderflow.order.domain.port.IdempotencyStore;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyStoreAdapter implements IdempotencyStore {

    private final IdempotencyJpaRepository jpa;
    private final Clock clock;

    public IdempotencyStoreAdapter(IdempotencyJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        return jpa.findById(key).map(entity -> new IdempotencyRecord(
                entity.getKey(),
                entity.getOrderId(),
                entity.getRequestHash()
        ));
    }

    @Override
    public void save(IdempotencyRecord record) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setKey(record.key());
        entity.setOrderId(record.orderId());
        entity.setRequestHash(record.requestHash());
        entity.setCreatedAt(clock.instant());
        jpa.save(entity);
    }
}
