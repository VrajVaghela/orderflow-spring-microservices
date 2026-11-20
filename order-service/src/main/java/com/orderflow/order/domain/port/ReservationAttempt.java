package com.orderflow.order.domain.port;

public sealed interface ReservationAttempt permits ReservationAttempt.Reserved, ReservationAttempt.Rejected {

    record Reserved(String reservationId) implements ReservationAttempt {
    }

    record Rejected(String reason) implements ReservationAttempt {
    }

    static Reserved reserved(String reservationId) {
        return new Reserved(reservationId);
    }

    static Rejected rejected(String reason) {
        return new Rejected(reason);
    }
}
