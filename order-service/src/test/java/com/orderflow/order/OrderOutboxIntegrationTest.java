package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.orderflow.common.Topics;
import com.orderflow.order.api.PlaceOrderRequest;
import com.orderflow.order.application.OrderResponse;
import com.orderflow.order.domain.port.InventoryReservationPort;
import com.orderflow.order.domain.port.ReservationAttempt;
import com.orderflow.order.domain.port.ReserveStockCommand;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "orderflow.outbox.poll-interval-ms=200",
                "orderflow.saga.recovery-interval-ms=600000",
                "orderflow.saga.payment-timeout-ms=600000",
                "orderflow.saga.placed-grace-ms=600000"
        }
)
@Testcontainers
@Import(OrderOutboxIntegrationTest.FakeInventory.class)
class OrderOutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    static final AtomicReference<ReservationAttempt> NEXT =
            new AtomicReference<>(ReservationAttempt.reserved("rsv-it"));
    static final AtomicInteger RESERVATIONS = new AtomicInteger();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private TestRestTemplate http;

    @Test
    void acceptedOrderIsWrittenWithItsOutboxEvent() {
        NEXT.set(ReservationAttempt.reserved("rsv-it"));
        int reservationsBefore = RESERVATIONS.get();
        ResponseEntity<OrderResponse> response = post(order("tok_visa_4242"), "idem-outbox-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().status()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-outbox");

        ResponseEntity<OrderResponse> replay = post(order("tok_visa_4242"), "idem-outbox-1");
        assertThat(replay.getBody().orderId()).isEqualTo(response.getBody().orderId());
        assertThat(RESERVATIONS.get() - reservationsBefore).isEqualTo(1);

        assertThat(publishedEvents()).anyMatch(body -> body.contains("ORDER_PAYMENT_REQUESTED"));
    }

    @Test
    void rejectedStockReturnsConflict() {
        NEXT.set(ReservationAttempt.rejected("insufficient stock for SKU-100"));
        ResponseEntity<OrderResponse> response = post(order("tok_visa_4242"), "idem-reject-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo("REJECTED");
    }

    private ResponseEntity<OrderResponse> post(PlaceOrderRequest request, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        headers.set("X-Correlation-Id", "corr-outbox");
        return http.postForEntity("/api/orders", new HttpEntity<>(request, headers), OrderResponse.class);
    }

    private static PlaceOrderRequest order(String token) {
        return new PlaceOrderRequest("cus_it", "USD", "CARD", token, List.of(new PlaceOrderRequest.Line("SKU-100", 1, 1500)));
    }

    private List<String> publishedEvents() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> seen = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(Topics.ORDER_EVENTS));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(400)).forEach(record -> seen.add(record.value()));
                assertThat(seen).anyMatch(body -> body.contains("ORDER_PAYMENT_REQUESTED"));
            });
        }
        return seen;
    }

    @TestConfiguration
    static class FakeInventory {
        @Bean
        @Primary
        InventoryReservationPort inventoryReservationPort() {
            return new InventoryReservationPort() {
                @Override
                public ReservationAttempt reserve(ReserveStockCommand command) {
                    RESERVATIONS.incrementAndGet();
                    return NEXT.get();
                }

                @Override
                public void release(UUID orderId) {
                }
            };
        }
    }
}
