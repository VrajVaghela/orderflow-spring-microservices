package com.orderflow.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    @Modifying
    @Query(value = """
            insert into outbox_events
                (id, aggregate_id, event_type, topic, payload, correlation_id, created_at, attempts)
            values
                (:id, :aggregateId, :eventType, :topic, :payload, :correlationId, :createdAt, 0)
            on conflict (id) do nothing
            """, nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("aggregateId") UUID aggregateId,
            @Param("eventType") String eventType,
            @Param("topic") String topic,
            @Param("payload") String payload,
            @Param("correlationId") String correlationId,
            @Param("createdAt") Instant createdAt
    );

    @Query("select e from OutboxEntity e where e.publishedAt is null order by e.createdAt asc")
    List<OutboxEntity> findUnpublished(Pageable pageable);

    @Modifying
    @Transactional
    @Query("update OutboxEntity e set e.publishedAt = :publishedAt where e.id = :id")
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Transactional
    @Query("""
            update OutboxEntity e
            set e.attempts = :attempts, e.lastError = :error, e.publishedAt = :publishedAt
            where e.id = :id
            """)
    int recordFailure(
            @Param("id") UUID id,
            @Param("error") String error,
            @Param("attempts") int attempts,
            @Param("publishedAt") Instant publishedAt
    );
}
