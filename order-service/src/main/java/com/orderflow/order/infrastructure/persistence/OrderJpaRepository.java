package com.orderflow.order.infrastructure.persistence;

import com.orderflow.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> lockById(@Param("id") UUID id);

    @Query("select o from OrderEntity o where o.customerId = :customerId order by o.createdAt desc")
    List<OrderEntity> findByCustomer(@Param("customerId") String customerId, Pageable pageable);

    @Query("""
            select o.id from OrderEntity o
            where o.status = :status and o.updatedAt < :updatedBefore
            order by o.updatedAt asc
            """)
    List<UUID> findIdsByStatusUpdatedBefore(
            @Param("status") OrderStatus status,
            @Param("updatedBefore") Instant updatedBefore,
            Pageable pageable
    );
}
