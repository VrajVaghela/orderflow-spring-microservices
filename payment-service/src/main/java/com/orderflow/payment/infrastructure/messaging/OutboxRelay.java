package com.orderflow.payment.infrastructure.messaging;

import com.orderflow.common.Correlation;
import com.orderflow.payment.infrastructure.persistence.OutboxEntity;
import com.orderflow.payment.infrastructure.persistence.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(
            OutboxRepository outbox,
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
        for (OutboxEntity message : outbox.findUnpublished(PageRequest.of(0, batchSize))) {
            if (message.getAttempts() >= maxAttempts) {
                deadLetter(message);
                continue;
            }
            try {
                send(message.getTopic(), message);
                markPublished(message);
            } catch (Exception ex) {
                log.warn("payment outbox publish failed id={}", message.getId(), ex);
                recordFailure(message, ex.getClass().getSimpleName(), false);
                break;
            }
        }
    }

    private void deadLetter(OutboxEntity message) {
        try {
            send(message.getTopic() + ".DLT", message);
            recordFailure(message, "dead-lettered", true);
        } catch (Exception ex) {
            log.error("failed to dead-letter payment event {}", message.getId(), ex);
        }
    }

    private void send(String topic, OutboxEntity message) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                message.getAggregateId().toString(),
                message.getPayload()
        );
        if (message.getCorrelationId() != null) {
            record.headers().add(Correlation.HEADER, message.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        kafka.send(record).get(5, TimeUnit.SECONDS);
    }

    @Transactional
    void markPublished(OutboxEntity message) {
        outbox.markPublished(message.getId(), clock.instant());
    }

    @Transactional
    void recordFailure(OutboxEntity message, String error, boolean dead) {
        String trimmed = error.substring(0, Math.min(error.length(), 512));
        outbox.recordFailure(
                message.getId(),
                trimmed,
                message.getAttempts() + 1,
                dead ? clock.instant() : null
        );
    }
}
