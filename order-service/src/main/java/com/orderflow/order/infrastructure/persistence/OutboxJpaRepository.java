package com.orderflow.order.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {

    @Query("select e from OutboxEntity e where e.publishedAt is null order by e.createdAt asc")
    List<OutboxEntity> findUnpublished(Pageable pageable);

    @Modifying
    @Query("update OutboxEntity e set e.publishedAt = :publishedAt where e.id = :id")
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Modifying
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
