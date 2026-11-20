package com.orderflow.query.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.EventType;
import com.orderflow.common.OrderLinePayload;
import com.orderflow.common.OrderSnapshot;
import com.orderflow.query.model.OrderViewDocument;
import com.orderflow.query.model.OrderViewRepository;
import com.orderflow.query.model.ProcessedEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderProjectionTest {

    @Mock
    private OrderViewRepository orders;
    @Mock
    private ProcessedEventRepository processed;

    private final EventSerde serde = new EventSerde();

    @Test
    void storesASnapshotAndDropsThePaymentToken() {
        when(processed.existsById(any())).thenReturn(false);
        when(orders.findById(any())).thenReturn(Optional.empty());
        OrderProjection projection = new OrderProjection(orders, processed, serde);
        UUID orderId = UUID.randomUUID();

        projection.apply(envelope(orderId, EventType.ORDER_PAYMENT_REQUESTED, 2, "PAYMENT_PENDING", "tok_secret"));

        ArgumentCaptor<OrderViewDocument> saved = ArgumentCaptor.forClass(OrderViewDocument.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("PAYMENT_PENDING");
        assertThat(saved.getValue().getTotalMinor()).isEqualTo(1999);
        assertThat(saved.getValue().getLines()).hasSize(1);
        assertThat(saved.getValue().toString()).doesNotContain("tok_secret");
    }

    @Test
    void ignoresAStaleVersion() {
        UUID orderId = UUID.randomUUID();
        OrderViewDocument current = new OrderViewDocument();
        current.setVersion(3);
        when(processed.existsById(any())).thenReturn(false);
        when(orders.findById(orderId.toString())).thenReturn(Optional.of(current));
        OrderProjection projection = new OrderProjection(orders, processed, serde);

        projection.apply(envelope(orderId, EventType.ORDER_PLACED, 1, "PLACED", null));

        verify(orders, never()).save(any());
    }

    private EventEnvelope envelope(UUID orderId, EventType type, long version, String status, String token) {
        OrderSnapshot snapshot = new OrderSnapshot(
                orderId, "cus_1", status, "USD", 1999,
                List.of(new OrderLinePayload("SKU-100", 1, 1999)),
                "CARD", token, null, null);
        return new EventEnvelope(
                UUID.randomUUID(), type.name(), Instant.parse("2026-11-20T12:00:00Z"),
                "corr", orderId, version, serde.toNode(snapshot));
    }
}
