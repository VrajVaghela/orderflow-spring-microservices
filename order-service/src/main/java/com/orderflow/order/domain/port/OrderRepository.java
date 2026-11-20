package com.orderflow.order.domain.port;

import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order insert(Order order);

    Order update(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByIdForUpdate(UUID id);

    List<Order> findByCustomerId(String customerId, int limit);

    List<UUID> findIdsByStatusUpdatedBefore(OrderStatus status, Instant updatedBefore, int limit);
}
