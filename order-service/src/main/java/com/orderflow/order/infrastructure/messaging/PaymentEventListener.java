package com.orderflow.order.infrastructure.messaging;

import com.orderflow.common.Correlation;
import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.PermanentMessageException;
import com.orderflow.common.Topics;
import com.orderflow.order.application.PaymentResultHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final EventSerde serde;
    private final PaymentResultHandler handler;

    public PaymentEventListener(EventSerde serde, PaymentResultHandler handler) {
        this.serde = serde;
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS)
    public void onPayment(ConsumerRecord<String, String> record) {
        String correlationId = KafkaCorrelation.read(record);
        if (correlationId != null) {
            Correlation.set(correlationId);
        }
        try {
            EventEnvelope envelope = serde.read(record.value());
            if (correlationId == null && envelope.correlationId() != null) {
                Correlation.set(envelope.correlationId());
            }
            handler.handle(envelope);
        } catch (PermanentMessageException ex) {
            log.error("dropping permanent payment message at offset {}", record.offset(), ex);
        } finally {
            Correlation.clear();
        }
    }
}
