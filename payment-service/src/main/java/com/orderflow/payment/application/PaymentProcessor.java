package com.orderflow.payment.application;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.EventType;
import com.orderflow.common.OrderSnapshot;
import com.orderflow.common.PaymentResultPayload;
import com.orderflow.common.Topics;
import com.orderflow.payment.domain.ChargeCommand;
import com.orderflow.payment.domain.PaymentMethod;
import com.orderflow.payment.domain.PaymentOutcome;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.PaymentStrategyFactory;
import com.orderflow.payment.infrastructure.persistence.InboxRepository;
import com.orderflow.payment.infrastructure.persistence.OutboxRepository;
import com.orderflow.payment.infrastructure.persistence.PaymentEntity;
import com.orderflow.payment.infrastructure.persistence.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentRepository payments;
    private final InboxRepository inbox;
    private final OutboxRepository outbox;
    private final PaymentStrategyFactory strategies;
    private final EventSerde serde;
    private final Clock clock;
    private final MeterRegistry registry;

    public PaymentProcessor(
            PaymentRepository payments,
            InboxRepository inbox,
            OutboxRepository outbox,
            PaymentStrategyFactory strategies,
            EventSerde serde,
            Clock clock,
            MeterRegistry registry
    ) {
        this.payments = payments;
        this.inbox = inbox;
        this.outbox = outbox;
        this.strategies = strategies;
        this.serde = serde;
        this.clock = clock;
        this.registry = registry;
    }

    @Transactional
    public void handle(EventEnvelope envelope) {
        if (!EventType.ORDER_PAYMENT_REQUESTED.name().equals(envelope.type())) {
            return;
        }
        if (inbox.existsById(envelope.eventId())) {
            return;
        }
        OrderSnapshot snapshot = serde.convert(envelope.payload(), OrderSnapshot.class);
        PaymentEntity payment = payments.findByOrderId(snapshot.orderId()).orElseGet(() -> charge(snapshot));
        publishResult(payment, envelope.correlationId());
        inbox.insertIgnore(envelope.eventId(), clock.instant());
    }

    private PaymentEntity charge(OrderSnapshot snapshot) {
        PaymentOutcome outcome = authorize(snapshot);
        PaymentEntity entity = new PaymentEntity();
        entity.setId(UUID.randomUUID());
        entity.setOrderId(snapshot.orderId());
        entity.setMethod(snapshot.paymentMethod());
        entity.setStatus(outcome.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
        entity.setAmountMinor(snapshot.totalMinor());
        entity.setCurrency(snapshot.currency());
        entity.setReason(outcome.reason());
        entity.setCreatedAt(clock.instant());
        PaymentEntity saved = payments.save(entity);
        registry.counter(
                "orderflow.payments",
                "result",
                saved.getStatus().name(),
                "method",
                saved.getMethod()
        ).increment();
        log.info("payment {} for order {} is {}", saved.getId(), saved.getOrderId(), saved.getStatus());
        return saved;
    }

    private PaymentOutcome authorize(OrderSnapshot snapshot) {
        try {
            PaymentMethod method = PaymentMethod.valueOf(snapshot.paymentMethod());
            return strategies.forMethod(method).charge(new ChargeCommand(
                    snapshot.orderId(),
                    snapshot.totalMinor(),
                    snapshot.currency(),
                    snapshot.paymentToken()
            ));
        } catch (RuntimeException ex) {
            log.warn("declining order {} because the payment method could not be charged", snapshot.orderId(), ex);
            return PaymentOutcome.decline("unsupported payment method");
        }
    }

    private void publishResult(PaymentEntity payment, String correlationId) {
        boolean authorized = payment.getStatus() == PaymentStatus.AUTHORIZED;
        EventType type = authorized ? EventType.PAYMENT_AUTHORIZED : EventType.PAYMENT_DECLINED;
        PaymentResultPayload payload = new PaymentResultPayload(
                payment.getId(),
                payment.getOrderId(),
                authorized,
                payment.getMethod(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getReason()
        );
        UUID eventId = resultEventId(payment.getId());
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                type.name(),
                clock.instant(),
                correlationId,
                payment.getOrderId(),
                1,
                serde.toNode(payload)
        );
        outbox.insertIgnore(
                eventId,
                payment.getOrderId(),
                type.name(),
                Topics.PAYMENT_EVENTS,
                serde.write(envelope),
                correlationId,
                envelope.occurredAt()
        );
    }

    public static UUID resultEventId(UUID paymentId) {
        return UUID.nameUUIDFromBytes(("payment-result:" + paymentId).getBytes(StandardCharsets.UTF_8));
    }
}
