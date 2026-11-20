package com.orderflow.order.domain.state;

import com.orderflow.order.domain.OrderStatus;
import org.springframework.stereotype.Component;

/**
 * Factory: rebuilds the state object from the status persisted in Postgres.
 * Callers never switch on the enum to pick a behavior class.
 */
@Component
public class OrderStateFactory {

    public OrderState from(OrderStatus status) {
        return switch (status) {
            case PLACED -> PlacedState.INSTANCE;
            case PAYMENT_PENDING -> PaymentPendingState.INSTANCE;
            case COMPENSATING -> CompensatingState.INSTANCE;
            case CONFIRMED -> ConfirmedState.INSTANCE;
            case CANCELLED -> CancelledState.INSTANCE;
            case REJECTED -> RejectedState.INSTANCE;
        };
    }
}
