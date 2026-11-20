package com.orderflow.order.infrastructure.messaging;

import com.orderflow.common.Correlation;
import com.orderflow.order.domain.port.OutboxMessage;
import com.orderflow.order.domain.port.OutboxStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(
            OutboxStore outbox,
            KafkaTemplate<String, String> kafka,
            Clock clock,
            @Value("${orderflow.outbox.batch-size:100}") int batchSize,
            @Value("${orderflow.outbox.max-attempts:10}") int maxAttempts
    ) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${orderflow.outbox.poll-interval-ms:400}")
    public void publish() {
        for (OutboxMessage message : outbox.unpublished(batchSize)) {
            if (message.attempts() >= maxAttempts) {
                deadLetter(message);
                continue;
            }
            try {
                send(message.topic(), message);
                outbox.markPublished(message.id(), clock.instant());
            } catch (Exception ex) {
                log.warn("outbox publish failed id={} type topic={}", message.id(), message.topic(), ex);
                outbox.recordFailure(message.id(), ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                        message.attempts() + 1, false, clock.instant());
                break;
            }
        }
    }

    private void deadLetter(OutboxMessage message) {
        try {
            send(message.topic() + ".DLT", message);
            outbox.recordFailure(message.id(), "dead-lettered", message.attempts(), true, clock.instant());
            log.error("dead-lettered outbox event {}", message.id());
        } catch (Exception ex) {
            log.error("failed to dead-letter outbox event {}", message.id(), ex);
        }
    }

    private void send(String topic, OutboxMessage message) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                message.aggregateId().toString(),
                message.payload()
        );
        if (message.correlationId() != null) {
            record.headers().add(Correlation.HEADER, message.correlationId().getBytes(StandardCharsets.UTF_8));
        }
        kafka.send(record).get(5, TimeUnit.SECONDS);
    }
}
