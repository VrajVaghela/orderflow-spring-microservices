package com.orderflow.order.infrastructure.persistence;

import com.orderflow.order.domain.Money;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderLine;
import com.orderflow.order.domain.state.OrderStateFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistenceMapper {

    private final OrderStateFactory states;

    public OrderPersistenceMapper(OrderStateFactory states) {
        this.states = states;
    }

    public OrderEntity newEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.id());
        entity.setCustomerId(order.customerId());
        entity.setStatus(order.status());
        entity.setCurrency(order.currency());
        entity.setTotalMinor(order.total().amountMinor());
        entity.setPaymentMethod(order.paymentMethod());
        entity.setPaymentToken(order.paymentToken());
        entity.setReservationId(order.reservationId());
        entity.setFailureReason(order.failureReason());
        entity.setCorrelationId(order.correlationId());
        entity.setAggregateVersion(order.aggregateVersion());
        entity.setCreatedAt(order.createdAt());
        entity.setUpdatedAt(order.updatedAt());
        int index = 0;
        for (OrderLine line : order.lines()) {
            OrderLineEntity row = new OrderLineEntity();
            row.setId(UUID.randomUUID());
            row.setSku(line.sku());
            row.setQuantity(line.quantity());
            row.setUnitPriceMinor(line.unitPrice().amountMinor());
            row.setLineIndex(index++);
            entity.addLine(row);
        }
        return entity;
    }

    public void copyMutable(Order order, OrderEntity entity) {
        entity.setStatus(order.status());
        entity.setReservationId(order.reservationId());
        entity.setFailureReason(order.failureReason());
        entity.setAggregateVersion(order.aggregateVersion());
        entity.setUpdatedAt(order.updatedAt());
    }

    public Order toDomain(OrderEntity entity) {
        List<OrderLineEntity> rows = new ArrayList<>(entity.getLines());
        rows.sort(Comparator.comparingInt(OrderLineEntity::getLineIndex));
        List<OrderLine> lines = rows.stream()
                .map(row -> new OrderLine(
                        row.getSku(),
                        row.getQuantity(),
                        new Money(row.getUnitPriceMinor(), entity.getCurrency())
                ))
                .toList();
        return Order.rehydrate(
                entity.getId(),
                entity.getCustomerId(),
                entity.getCurrency(),
                entity.getPaymentMethod(),
                entity.getPaymentToken(),
                lines,
                new Money(entity.getTotalMinor(), entity.getCurrency()),
                entity.getCreatedAt(),
                entity.getCorrelationId(),
                states.from(entity.getStatus()),
                entity.getAggregateVersion(),
                entity.getLockVersion(),
                entity.getReservationId(),
                entity.getFailureReason(),
                entity.getUpdatedAt()
        );
    }
}
