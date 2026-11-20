package com.orderflow.order.application;

import com.orderflow.order.domain.InventoryTemporarilyUnavailableException;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.domain.port.InventoryReservationPort;
import com.orderflow.order.domain.port.OrderRepository;
import com.orderflow.order.domain.port.ReservationAttempt;
import com.orderflow.order.domain.port.ReserveStockCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SagaRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryJob.class);

    private final OrderRepository orders;
    private final OrderTransaction tx;
    private final InventoryReservationPort inventory;
    private final PaymentResultHandler payments;
    private final Clock clock;
    private final long placedGraceMs;
    private final long paymentTimeoutMs;

    public SagaRecoveryJob(
            OrderRepository orders,
            OrderTransaction tx,
            InventoryReservationPort inventory,
            PaymentResultHandler payments,
            Clock clock,
            @Value("${orderflow.saga.placed-grace-ms:3000}") long placedGraceMs,
            @Value("${orderflow.saga.payment-timeout-ms:600000}") long paymentTimeoutMs
    ) {
        this.orders = orders;
        this.tx = tx;
        this.inventory = inventory;
        this.payments = payments;
        this.clock = clock;
        this.placedGraceMs = placedGraceMs;
        this.paymentTimeoutMs = paymentTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${orderflow.saga.recovery-interval-ms:2000}")
    public void recover() {
        Instant now = clock.instant();
        recoverPlaced(now.minusMillis(placedGraceMs));
        timeoutPayments(now.minusMillis(paymentTimeoutMs));
        recoverCompensating(now.plusSeconds(1));
    }

    private void recoverPlaced(Instant updatedBefore) {
        for (UUID id : orders.findIdsByStatusUpdatedBefore(OrderStatus.PLACED, updatedBefore, 50)) {
            Order order = orders.findById(id).orElse(null);
            if (order == null || order.status() != OrderStatus.PLACED) {
                continue;
            }
            try {
                ReservationAttempt attempt = inventory.reserve(toReserve(order));
                tx.applyReservation(id, attempt);
            } catch (InventoryTemporarilyUnavailableException ex) {
                log.warn("inventory still unavailable for placed order {}", id);
            } catch (RuntimeException ex) {
                log.error("failed to recover placed order {}", id, ex);
            }
        }
    }

    private void timeoutPayments(Instant updatedBefore) {
        for (UUID id : orders.findIdsByStatusUpdatedBefore(OrderStatus.PAYMENT_PENDING, updatedBefore, 50)) {
            try {
                if (tx.timeoutPayment(id)) {
                    payments.releaseAndFinish(id, null);
                }
            } catch (InventoryTemporarilyUnavailableException ex) {
                log.warn("could not release stock after payment timeout for {}", id);
            } catch (RuntimeException ex) {
                log.error("failed to time out payment for {}", id, ex);
            }
        }
    }

    private void recoverCompensating(Instant updatedBefore) {
        for (UUID id : orders.findIdsByStatusUpdatedBefore(OrderStatus.COMPENSATING, updatedBefore, 50)) {
            try {
                payments.releaseAndFinish(id, null);
            } catch (InventoryTemporarilyUnavailableException ex) {
                log.warn("release still failing for compensating order {}", id);
            } catch (RuntimeException ex) {
                log.error("failed to finish compensation for {}", id, ex);
            }
        }
    }

    private static ReserveStockCommand toReserve(Order order) {
        List<ReserveStockCommand.Line> lines = order.lines().stream()
                .map(line -> new ReserveStockCommand.Line(line.sku(), line.quantity()))
                .toList();
        return new ReserveStockCommand(order.id(), lines);
    }
}
