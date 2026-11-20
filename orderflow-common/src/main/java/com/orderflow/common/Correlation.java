package com.orderflow.common;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Process-wide correlation id carried in the {@code X-Correlation-Id} header,
 * Kafka record headers, gRPC metadata, and the SLF4J MDC.
 */
public final class Correlation {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final int MAX_LENGTH = 128;

    private Correlation() {
    }

    public static Optional<String> current() {
        String value = MDC.get(MDC_KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public static void set(String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(MDC_KEY);
            return;
        }
        MDC.put(MDC_KEY, value);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /**
     * Accepts a caller-supplied id when it is short enough to log safely.
     * Otherwise mints a new one so a hostile header cannot blow up a log line.
     */
    public static String resolve(String incoming) {
        if (incoming == null) {
            return UUID.randomUUID().toString();
        }
        String trimmed = incoming.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return UUID.randomUUID().toString();
        }
        return trimmed;
    }
}
