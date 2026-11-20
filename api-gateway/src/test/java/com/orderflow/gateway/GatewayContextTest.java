package com.orderflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayContextTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthIsUpAndTheCorrelationHeaderIsEchoed() {
        webTestClient.get()
                .uri("/actuator/health")
                .header("X-Correlation-Id", "gw-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "gw-1")
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("UP"));
    }

    @Test
    void mintsACorrelationIdWhenTheCallerDoesNot() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Correlation-Id", value -> assertThat(value).isNotBlank());
    }
}
