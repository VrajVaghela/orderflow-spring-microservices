package com.orderflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.EventType;
import com.orderflow.common.OrderLinePayload;
import com.orderflow.common.OrderSnapshot;
import com.orderflow.common.Topics;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.infrastructure.persistence.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "orderflow.outbox.poll-interval-ms=200")
@Testcontainers
class PaymentProcessorIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private PaymentRepository payments;

    private final EventSerde serde = new EventSerde();

    @Test
    void consumesAPaymentRequestOnceAndRecordsTheDecision() throws Exception {
        UUID orderId = UUID.randomUUID();
        String json = serde.write(envelope(orderId, "tok_decline_0002"));

        kafka.send(Topics.ORDER_EVENTS, orderId.toString(), json).get(10, TimeUnit.SECONDS);
        kafka.send(Topics.ORDER_EVENTS, orderId.toString(), json).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).until(() -> payments.findByOrderId(orderId).isPresent());

        var payment = payments.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getReason()).isEqualTo("card declined");
        assertThat(payments.findAll()).filteredOn(row -> row.getOrderId().equals(orderId)).hasSize(1);
    }

    private EventEnvelope envelope(UUID orderId, String token) {
        OrderSnapshot snapshot = new OrderSnapshot(
                orderId,
                "cus_pay",
                "PAYMENT_PENDING",
                "USD",
                2500,
                List.of(new OrderLinePayload("SKU-100", 1, 2500)),
                "CARD",
                token,
                "rsv",
                null
        );
        return new EventEnvelope(
                UUID.randomUUID(),
                EventType.ORDER_PAYMENT_REQUESTED.name(),
                Instant.now(),
                "corr-pay",
                orderId,
                2,
                serde.toNode(snapshot)
        );
    }
}
