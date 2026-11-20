package com.orderflow.order.infrastructure.messaging;

import com.orderflow.common.Correlation;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

public final class KafkaCorrelation {

    private KafkaCorrelation() {
    }

    public static String read(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(Correlation.HEADER);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
