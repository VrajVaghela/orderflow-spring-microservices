package com.orderflow.query.messaging;

import com.orderflow.common.Correlation;
import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.PermanentMessageException;
import com.orderflow.common.Topics;
import com.orderflow.query.projection.OrderProjection;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final EventSerde serde;
    private final OrderProjection projection;

    public OrderEventListener(EventSerde serde, OrderProjection projection) {
        this.serde = serde;
        this.projection = projection;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        String correlationId = correlation(record);
        if (correlationId != null) {
            Correlation.set(correlationId);
        }
        try {
            EventEnvelope envelope = serde.read(record.value());
            if (correlationId == null && envelope.correlationId() != null) {
                Correlation.set(envelope.correlationId());
            }
            projection.apply(envelope);
        } catch (PermanentMessageException ex) {
            log.error("dropping permanent order message at offset {}", record.offset(), ex);
        } finally {
            Correlation.clear();
        }
    }

    private static String correlation(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(Correlation.HEADER);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
