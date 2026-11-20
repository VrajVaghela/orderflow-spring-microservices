package com.orderflow.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.orderflow.common.EventEnvelope;
import com.orderflow.common.EventSerde;
import com.orderflow.common.EventType;
import com.orderflow.common.OrderLinePayload;
import com.orderflow.common.OrderSnapshot;
import com.orderflow.common.Topics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureGraphQlTester
@Testcontainers
class QueryProjectionIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("orderflow"));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private GraphQlTester graphQl;

    private final EventSerde serde = new EventSerde();

    @Test
    void projectsTheNewestSnapshotThroughGraphql() throws Exception {
        UUID orderId = UUID.randomUUID();
        kafka.send(Topics.ORDER_EVENTS, orderId.toString(), event(orderId, 2, "CONFIRMED", EventType.ORDER_CONFIRMED))
                .get(10, TimeUnit.SECONDS);
        kafka.send(Topics.ORDER_EVENTS, orderId.toString(), event(orderId, 1, "PLACED", EventType.ORDER_PLACED))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                graphQl.document("{ order(id: \"%s\") { status version totalMinor customerId } }".formatted(orderId))
                        .execute()
                        .path("order.status").entity(String.class).isEqualTo("CONFIRMED")
                        .path("order.version").entity(Integer.class).isEqualTo(2)
                        .path("order.totalMinor").entity(Integer.class).isEqualTo(1999)
                        .path("order.customerId").entity(String.class).isEqualTo("cus_q"));

        String listed = graphQl.document("{ ordersByCustomer(customerId: \"cus_q\") { id } }")
                .execute()
                .path("ordersByCustomer[0].id")
                .entity(String.class)
                .get();
        assertThat(listed).isEqualTo(orderId.toString());
    }

    private String event(UUID orderId, long version, String status, EventType type) {
        OrderSnapshot snapshot = new OrderSnapshot(
                orderId, "cus_q", status, "USD", 1999,
                List.of(new OrderLinePayload("SKU-100", 1, 1999)),
                "CARD", "tok_should_not_be_stored", null, null);
        return serde.write(new EventEnvelope(
                UUID.randomUUID(), type.name(), Instant.now(), "corr-q", orderId, version, serde.toNode(snapshot)));
    }
}
