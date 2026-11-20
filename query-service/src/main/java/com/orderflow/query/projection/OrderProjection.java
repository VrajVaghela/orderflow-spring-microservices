package com.orderflow.query.projection;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.OrderLinePayload;
import com.orderflow.common.OrderSnapshot;
import com.orderflow.query.model.OrderViewDocument;
import com.orderflow.query.model.OrderViewRepository;
import com.orderflow.query.model.ProcessedEventDocument;
import com.orderflow.query.model.ProcessedEventRepository;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Applies full order snapshots. An older or duplicate version is ignored,
 * so the read model does not need a Mongo transaction.
 * The payment token is never copied into the document.
 */
@Service
public class OrderProjection {

    private final OrderViewRepository orders;
    private final ProcessedEventRepository processed;
    private final EventSerde serde;

    public OrderProjection(OrderViewRepository orders, ProcessedEventRepository processed, EventSerde serde) {
        this.orders = orders;
        this.processed = processed;
        this.serde = serde;
    }

    public void apply(EventEnvelope envelope) {
        if (envelope.type() == null || !envelope.type().startsWith("ORDER_")) {
            return;
        }
        if (processed.existsById(envelope.eventId().toString())) {
            return;
        }
        OrderSnapshot snapshot = serde.convert(envelope.payload(), OrderSnapshot.class);
        OrderViewDocument current = orders.findById(snapshot.orderId().toString()).orElse(null);
        if (current != null && current.getVersion() >= envelope.aggregateVersion()) {
            markProcessed(envelope.eventId().toString());
            return;
        }
        OrderViewDocument document = current == null ? new OrderViewDocument() : current;
        document.setId(snapshot.orderId().toString());
        document.setCustomerId(snapshot.customerId());
        document.setStatus(snapshot.status());
        document.setCurrency(snapshot.currency());
        document.setTotalMinor(snapshot.totalMinor());
        document.setPaymentMethod(snapshot.paymentMethod());
        document.setReservationId(snapshot.reservationId());
        document.setReason(snapshot.reason());
        document.setVersion(envelope.aggregateVersion());
        document.setUpdatedAt(envelope.occurredAt());
        document.setLines(toLines(snapshot.lines()));
        orders.save(document);
        markProcessed(envelope.eventId().toString());
    }

    private void markProcessed(String eventId) {
        if (processed.existsById(eventId)) {
            return;
        }
        try {
            processed.save(new ProcessedEventDocument(eventId));
        } catch (DuplicateKeyException ignored) {
            // a concurrent delivery won the insert
        }
    }

    private static List<OrderViewDocument.Line> toLines(List<OrderLinePayload> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(line -> new OrderViewDocument.Line(line.sku(), line.quantity(), line.unitPriceMinor()))
                .toList();
    }
}
