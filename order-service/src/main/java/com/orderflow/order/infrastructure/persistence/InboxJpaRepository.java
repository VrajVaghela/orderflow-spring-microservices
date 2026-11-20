package com.orderflow.order.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxJpaRepository extends JpaRepository<InboxEntity, UUID> {

    @Modifying
    @Query(value = """
            insert into processed_events (event_id, processed_at)
            values (:id, :processedAt)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertIgnore(@Param("id") UUID id, @Param("processedAt") Instant processedAt);
}
