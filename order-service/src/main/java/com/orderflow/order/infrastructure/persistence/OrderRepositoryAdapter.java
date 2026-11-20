package com.orderflow.order.infrastructure.persistence;

import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.port.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpa;
    private final OrderPersistenceMapper mapper;

    public OrderRepositoryAdapter(OrderJpaRepository jpa, OrderPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Order insert(Order order) {
        OrderEntity saved = jpa.save(mapper.newEntity(order));
        return mapper.toDomain(saved);
    }

    @Override
    public Order update(Order order) {
        OrderEntity entity = jpa.findById(order.id()).orElseThrow();
        mapper.copyMutable(order, entity);
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Order> findByIdForUpdate(UUID id) {
        return jpa.lockById(id).map(entity -> {
            entity.getLines().size();
            return mapper.toDomain(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByCustomerId(String customerId, int limit) {
        return jpa.findByCustomer(customerId, PageRequest.of(0, limit)).stream()
                .map(entity -> {
                    entity.getLines().size();
                    return mapper.toDomain(entity);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findIdsByStatusUpdatedBefore(OrderStatus status, Instant updatedBefore, int limit) {
        return jpa.findIdsByStatusUpdatedBefore(status, updatedBefore, PageRequest.of(0, limit));
    }
}
