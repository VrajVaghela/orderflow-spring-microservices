package com.orderflow.order.domain.port;

import java.util.Optional;

public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String key);

    void save(IdempotencyRecord record);
}
