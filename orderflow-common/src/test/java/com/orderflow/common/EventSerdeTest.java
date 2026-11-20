package com.orderflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventSerdeTest {

    private final EventSerde serde = new EventSerde();

    @Test
    void roundTripsAnOrderSnapshot() {
        UUID orderId = UUID.randomUUID();
        OrderSnapshot snapshot = new OrderSnapshot(
                orderId,
                "cus_1",
                "PAYMENT_PENDING",
                "USD",
                3998,
                List.of(new OrderLinePayload("SKU-100", 2, 1999)),
                "CARD",
                "tok_visa_4242",
                "rsv-1",
                null
        );
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.ORDER_PAYMENT_REQUESTED.name(),
                Instant.parse("2026-11-20T12:00:00Z"),
                "corr-1",
                orderId,
                2,
                serde.toNode(snapshot)
        );

        EventEnvelope read = serde.read(serde.write(envelope));

        assertThat(read.eventId()).isEqualTo(envelope.eventId());
        assertThat(read.occurredAt()).isEqualTo(Instant.parse("2026-11-20T12:00:00Z"));
        assertThat(read.aggregateVersion()).isEqualTo(2);
        OrderSnapshot payload = serde.convert(read.payload(), OrderSnapshot.class);
        assertThat(payload).isEqualTo(snapshot);
    }

    @Test
    void rejectsAPayloadThatIsNotJson() {
        assertThatThrownBy(() -> serde.read("not-json"))
                .isInstanceOf(PermanentMessageException.class);
    }
}
