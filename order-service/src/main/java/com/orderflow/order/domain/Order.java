package com.orderflow.order.domain;

import com.orderflow.common.EventType;
import com.orderflow.common.OrderLinePayload;
import com.orderflow.common.OrderSnapshot;
import com.orderflow.order.domain.event.OrderDomainEvent;
import com.orderflow.order.domain.state.OrderState;
import com.orderflow.order.domain.state.OrderStateFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Order aggregate. Structural invariants are enforced by the builder.
 * Status changes go through {@link OrderState}. Integration events are
 * collected and pulled by the application service inside the same transaction
 * that writes the outbox.
 */
public final class Order {

    private final UUID id;
    private final String customerId;
    private final CurrencyCode currency;
    private final PaymentMethod paymentMethod;
    private final String paymentToken;
    private final List<OrderLine> lines;
    private final Money total;
    private final Instant createdAt;
    private final String correlationId;
    private OrderState state;
    private long aggregateVersion;
    private Long lockVersion;
    private String reservationId;
    private String failureReason;
    private Instant updatedAt;
    private final List<OrderDomainEvent> pendingEvents = new ArrayList<>();

    private Order(
            UUID id,
            String customerId,
            CurrencyCode currency,
            PaymentMethod paymentMethod,
            String paymentToken,
            List<OrderLine> lines,
            Money total,
            Instant createdAt,
            String correlationId,
            OrderState state,
            long aggregateVersion,
            Long lockVersion,
            String reservationId,
            String failureReason,
            Instant updatedAt
    ) {
        this.id = id;
        this.customerId = customerId;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.paymentToken = paymentToken;
        this.lines = lines;
        this.total = total;
        this.createdAt = createdAt;
        this.correlationId = correlationId;
        this.state = state;
        this.aggregateVersion = aggregateVersion;
        this.lockVersion = lockVersion;
        this.reservationId = reservationId;
        this.failureReason = failureReason;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Order rehydrate(
            UUID id,
            String customerId,
            CurrencyCode currency,
            PaymentMethod paymentMethod,
            String paymentToken,
            List<OrderLine> lines,
            Money total,
            Instant createdAt,
            String correlationId,
            OrderState state,
            long aggregateVersion,
            Long lockVersion,
            String reservationId,
            String failureReason,
            Instant updatedAt
    ) {
        return new Order(
                id,
                customerId,
                currency,
                paymentMethod,
                paymentToken,
                List.copyOf(lines),
                total,
                createdAt,
                correlationId,
                state,
                aggregateVersion,
                lockVersion,
                reservationId,
                failureReason,
                updatedAt
        );
    }

    public void markPaymentPending(String reservationId, Instant now) {
        this.state = state.requestPayment();
        this.reservationId = reservationId;
        record(EventType.ORDER_PAYMENT_REQUESTED, now, true);
    }

    public void reject(String reason, Instant now) {
        this.state = state.reject();
        this.failureReason = reason;
        record(EventType.ORDER_REJECTED, now, false);
    }

    public void confirm(Instant now) {
        this.state = state.confirm();
        record(EventType.ORDER_CONFIRMED, now, false);
    }

    public void beginCompensation(String reason, Instant now) {
        this.state = state.beginCompensation();
        this.failureReason = reason;
        this.updatedAt = now;
        this.aggregateVersion++;
    }

    public void finishCancellation(Instant now) {
        this.state = state.finishCancellation();
        record(EventType.ORDER_CANCELLED, now, false);
    }

    public List<OrderDomainEvent> pullEvents() {
        List<OrderDomainEvent> copy = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return copy;
    }

    private void record(EventType type, Instant now, boolean includeToken) {
        this.updatedAt = now;
        this.aggregateVersion++;
        pendingEvents.add(new OrderDomainEvent(
                UUID.randomUUID(),
                id,
                aggregateVersion,
                now,
                correlationId,
                type,
                snapshot(includeToken)
        ));
    }

    private OrderSnapshot snapshot(boolean includeToken) {
        List<OrderLinePayload> payloads = lines.stream()
                .map(line -> new OrderLinePayload(line.sku(), line.quantity(), line.unitPrice().amountMinor()))
                .toList();
        return new OrderSnapshot(
                id,
                customerId,
                state.status().name(),
                currency.name(),
                total.amountMinor(),
                payloads,
                paymentMethod.name(),
                includeToken ? paymentToken : null,
                reservationId,
                failureReason
        );
    }

    public UUID id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public CurrencyCode currency() {
        return currency;
    }

    public PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    public String paymentToken() {
        return paymentToken;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public Money total() {
        return total;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String correlationId() {
        return correlationId;
    }

    public OrderStatus status() {
        return state.status();
    }

    public long aggregateVersion() {
        return aggregateVersion;
    }

    public Long lockVersion() {
        return lockVersion;
    }

    public String reservationId() {
        return reservationId;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public static final class Builder {
        private UUID id;
        private String customerId;
        private CurrencyCode currency;
        private PaymentMethod paymentMethod;
        private String paymentToken;
        private List<OrderLine> lines = List.of();
        private Instant now;
        private String correlationId;
        private OrderStateFactory states;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder currency(CurrencyCode currency) {
            this.currency = currency;
            return this;
        }

        public Builder paymentMethod(PaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder paymentToken(String paymentToken) {
            this.paymentToken = paymentToken;
            return this;
        }

        public Builder lines(List<OrderLine> lines) {
            this.lines = lines == null ? List.of() : List.copyOf(lines);
            return this;
        }

        public Builder now(Instant now) {
            this.now = now;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder states(OrderStateFactory states) {
            this.states = states;
            return this;
        }

        public Order build() {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(customerId, "customerId");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(paymentMethod, "paymentMethod");
            Objects.requireNonNull(now, "now");
            Objects.requireNonNull(states, "states");
            Objects.requireNonNull(correlationId, "correlationId");
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("order requires at least one line");
            }
            Money total = Money.zero(currency);
            for (OrderLine line : lines) {
                if (line.unitPrice().currency() != currency) {
                    throw new IllegalArgumentException("line currency does not match the order");
                }
                total = total.plus(line.lineTotal());
            }
            Order order = new Order(
                    id,
                    customerId,
                    currency,
                    paymentMethod,
                    paymentToken,
                    lines,
                    total,
                    now,
                    correlationId,
                    states.from(OrderStatus.PLACED),
                    0,
                    null,
                    null,
                    null,
                    now
            );
            order.record(EventType.ORDER_PLACED, now, false);
            return order;
        }
    }
}
