package com.orderflow.order.application;

import com.orderflow.common.PaymentResultPayload;
import com.orderflow.order.domain.CurrencyCode;
import com.orderflow.order.domain.Money;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderLine;
import com.orderflow.order.domain.OrderNotFoundException;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.PaymentMethod;
import com.orderflow.order.domain.event.DomainEventPublisher;
import com.orderflow.order.domain.port.IdempotencyRecord;
import com.orderflow.order.domain.port.IdempotencyStore;
import com.orderflow.order.domain.port.InboxStore;
import com.orderflow.order.domain.port.OrderRepository;
import com.orderflow.order.domain.port.ReservationAttempt;
import com.orderflow.order.domain.state.OrderStateFactory;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTransaction {

    private static final Logger log = LoggerFactory.getLogger(OrderTransaction.class);

    private final OrderRepository orders;
    private final IdempotencyStore idempotency;
    private final InboxStore inbox;
    private final DomainEventPublisher publisher;
    private final OrderStateFactory states;
    private final Clock clock;

    public OrderTransaction(
            OrderRepository orders,
            IdempotencyStore idempotency,
            InboxStore inbox,
            DomainEventPublisher publisher,
            OrderStateFactory states,
            Clock clock
    ) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.inbox = inbox;
        this.publisher = publisher;
        this.states = states;
        this.clock = clock;
    }

    @Transactional
    public Order create(
            UUID id,
            PlaceOrderCommand command,
            String correlationId,
            String idempotencyKey,
            String requestHash
    ) {
        CurrencyCode currency = CurrencyCode.valueOf(command.currency());
        List<OrderLine> lines = command.lines().stream()
                .map(line -> new OrderLine(line.sku(), line.quantity(), new Money(line.unitPriceMinor(), currency)))
                .toList();
        Order order = Order.builder()
                .id(id)
                .customerId(command.customerId())
                .currency(currency)
                .paymentMethod(PaymentMethod.valueOf(command.paymentMethod()))
                .paymentToken(command.paymentToken())
                .lines(lines)
                .now(clock.instant())
                .correlationId(correlationId)
                .states(states)
                .build();
        Order saved = orders.insert(order);
        if (idempotencyKey != null) {
            idempotency.save(new IdempotencyRecord(idempotencyKey, id, requestHash));
        }
        publisher.publishAll(order.pullEvents());
        return saved;
    }

    @Transactional
    public Order applyReservation(UUID id, ReservationAttempt attempt) {
        Order order = orders.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (order.status() != OrderStatus.PLACED) {
            return order;
        }
        switch (attempt) {
            case ReservationAttempt.Reserved reserved -> order.markPaymentPending(reserved.reservationId(), clock.instant());
            case ReservationAttempt.Rejected rejected -> order.reject(rejected.reason(), clock.instant());
        }
        Order saved = orders.update(order);
        publisher.publishAll(order.pullEvents());
        return saved;
    }

    @Transactional
    public void onPaymentAuthorized(PaymentResultPayload result, UUID eventId) {
        if (inbox.alreadyProcessed(eventId)) {
            return;
        }
        Order order = orders.findByIdForUpdate(result.orderId())
                .orElseThrow(() -> new OrderNotFoundException(result.orderId()));
        if (order.status() == OrderStatus.PAYMENT_PENDING) {
            order.confirm(clock.instant());
            orders.update(order);
            publisher.publishAll(order.pullEvents());
        } else {
            log.warn("ignoring payment authorization for order {} in status {}", order.id(), order.status());
        }
        inbox.markProcessed(eventId, clock.instant());
    }

    /**
     * @return true when the caller must release stock and then finish compensation
     */
    @Transactional
    public boolean beginCompensationIfNeeded(PaymentResultPayload result, UUID eventId) {
        if (inbox.alreadyProcessed(eventId)) {
            return false;
        }
        Order order = orders.findByIdForUpdate(result.orderId())
                .orElseThrow(() -> new OrderNotFoundException(result.orderId()));
        if (order.status() == OrderStatus.PAYMENT_PENDING) {
            String reason = result.reason() == null ? "payment declined" : result.reason();
            order.beginCompensation(reason, clock.instant());
            orders.update(order);
            return true;
        }
        if (order.status() == OrderStatus.COMPENSATING) {
            return true;
        }
        inbox.markProcessed(eventId, clock.instant());
        return false;
    }

    @Transactional
    public boolean timeoutPayment(UUID id) {
        Order order = orders.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (order.status() != OrderStatus.PAYMENT_PENDING) {
            return false;
        }
        order.beginCompensation("payment timed out", clock.instant());
        orders.update(order);
        return true;
    }

    @Transactional
    public void finishCompensation(UUID orderId, UUID eventId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.status() == OrderStatus.COMPENSATING) {
            order.finishCancellation(clock.instant());
            orders.update(order);
            publisher.publishAll(order.pullEvents());
        }
        if (eventId != null) {
            inbox.markProcessed(eventId, clock.instant());
        }
    }
}
