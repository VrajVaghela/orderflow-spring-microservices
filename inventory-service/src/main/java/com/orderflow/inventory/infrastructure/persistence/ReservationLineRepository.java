package com.orderflow.inventory.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationLineRepository extends JpaRepository<ReservationLineEntity, ReservationLineId> {

    @Query("select l from ReservationLineEntity l where l.id.orderId = :orderId")
    List<ReservationLineEntity> findByOrderId(@Param("orderId") String orderId);
}
