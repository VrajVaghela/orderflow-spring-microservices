package com.orderflow.order.domain.port;

import java.util.UUID;

public interface InventoryReservationPort {

    ReservationAttempt reserve(ReserveStockCommand command);

    void release(UUID orderId);
}
