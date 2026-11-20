package com.orderflow.payment.infrastructure.messaging;

import com.orderflow.common.Correlation;
import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.PermanentMessageException;
import com.orderflow.common.Topics;
import com.orderflow.payment.application.PaymentProcessor;
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
    private final PaymentProcessor processor;

    public OrderEventListener(EventSerde serde, PaymentProcessor processor) {
        this.serde = serde;
        this.processor = processor;
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
            processor.handle(envelope);
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
