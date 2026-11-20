package com.orderflow.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the packaged services as separate processes so each one keeps its own
 * classpath, and drives a happy-path saga plus a compensating decline.
 */
@Testcontainers
class OrderSagaEndToEndTest {

    private static final Pattern ORDER_ID = Pattern.compile("\"orderId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern AVAILABLE = Pattern.compile("\"available\"\\s*:\\s*(\\d+)");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<Process> PROCESSES = new ArrayList<>();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    static int orderPort;
    static int inventoryPort;
    static int paymentPort;
    static int queryPort;
    static int grpcPort;

    @BeforeAll
    static void startSystem() throws Exception {
        createDatabase("orders");
        createDatabase("inventory");
        createDatabase("payment");
        orderPort = 18081;
        inventoryPort = 18082;
        paymentPort = 18083;
        queryPort = 18084;
        grpcPort = 19090;
        String kafka = KAFKA.getBootstrapServers();
        start("inventory-service", inventoryPort,
                "--server.port=" + inventoryPort,
                "--grpc.server.port=" + grpcPort,
                "--spring.datasource.url=" + jdbc("inventory"),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
        start("order-service", orderPort,
                "--server.port=" + orderPort,
                "--spring.datasource.url=" + jdbc("orders"),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.kafka.bootstrap-servers=" + kafka,
                "--orderflow.inventory.host=localhost",
                "--orderflow.inventory.port=" + grpcPort,
                "--orderflow.outbox.poll-interval-ms=200",
                "--orderflow.saga.recovery-interval-ms=500",
                "--orderflow.saga.placed-grace-ms=2000",
                "--orderflow.saga.payment-timeout-ms=600000");
        start("payment-service", paymentPort,
                "--server.port=" + paymentPort,
                "--spring.datasource.url=" + jdbc("payment"),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.kafka.bootstrap-servers=" + kafka,
                "--orderflow.outbox.poll-interval-ms=200");
        start("query-service", queryPort,
                "--server.port=" + queryPort,
                "--spring.data.mongodb.uri=" + MONGO.getReplicaSetUrl("orderflow"),
                "--spring.kafka.bootstrap-servers=" + kafka);
        await().atMost(Duration.ofSeconds(120)).until(() ->
                healthy(inventoryPort) && healthy(orderPort) && healthy(paymentPort) && healthy(queryPort));
    }

    @AfterAll
    static void stopSystem() {
        for (Process process : PROCESSES) {
            process.destroy();
        }
        for (Process process : PROCESSES) {
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    @Test
    void confirmedOrderAndDeclinedOrderCompensateStock() throws Exception {
        String confirmed = place("cus_e2e", "SKU-100", 2, "tok_visa_4242");
        awaitStatus(confirmed, "CONFIRMED");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(graphqlStatus(confirmed)).isEqualTo("CONFIRMED"));
        assertThat(available("SKU-100")).isEqualTo(999_998);

        String cancelled = place("cus_e2e", "SKU-200", 1, "tok_decline_0002");
        awaitStatus(cancelled, "CANCELLED");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(graphqlStatus(cancelled)).isEqualTo("CANCELLED"));
        assertThat(available("SKU-200")).isEqualTo(1_000_000);
    }

    private static String place(String customer, String sku, int quantity, String token) throws Exception {
        String body = """
                {"customerId":"%s","currency":"USD","paymentMethod":"CARD","paymentToken":"%s","lines":[{"sku":"%s","quantity":%d,"unitPriceMinor":1500}]}
                """.formatted(customer, token, sku, quantity);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + orderPort + "/api/orders"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", "e2e-" + token)
                .header("Idempotency-Key", "e2e-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(202);
        Matcher matcher = ORDER_ID.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static void awaitStatus(String orderId, String expected) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(orderStatus(orderId)).isEqualTo(expected));
    }

    private static String orderStatus(String orderId) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + orderPort + "/api/orders/" + orderId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher matcher = STATUS.matcher(response.body());
        return matcher.find() ? matcher.group(1) : response.body();
    }

    private static String graphqlStatus(String orderId) throws Exception {
        String query = "{\"query\":\"{ order(id: \\\"%s\\\") { status } }\"}".formatted(orderId);
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + queryPort + "/graphql"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(query))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher matcher = STATUS.matcher(response.body());
        assertThat(matcher.find()).as(response.body()).isTrue();
        return matcher.group(1);
    }

    private static int available(String sku) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + inventoryPort + "/api/stock/" + sku)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher matcher = AVAILABLE.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean healthy(int port) {
        try {
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().timeout(Duration.ofSeconds(2)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
        } catch (Exception ex) {
            return false;
        }
    }

    private static void createDatabase(String name) throws Exception {
        org.testcontainers.containers.Container.ExecResult result = POSTGRES.execInContainer(
                "psql", "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
                "-c", "CREATE DATABASE " + name);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(result.getStderr());
        }
    }

    private static String jdbc(String database) {
        String url = POSTGRES.getJdbcUrl();
        return url.substring(0, url.lastIndexOf('/') + 1) + database;
    }

    private static void start(String module, int port, String... args) throws IOException {
        Path jar = Path.of("..", module, "target", module + "-exec.jar").toAbsolutePath().normalize();
        if (!Files.exists(jar)) {
            throw new IllegalStateException("missing " + jar + "; run mvn package first");
        }
        Path logs = Path.of("target", "e2e-logs");
        Files.createDirectories(logs);
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx320m");
        command.add("-jar");
        command.add(jar.toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logs.resolve(module + ".log").toFile())
                .start();
        PROCESSES.add(process);
        process.onExit().thenAccept(done -> {
            if (done.exitValue() != 0) {
                System.err.println(module + " exited with " + done.exitValue() + " while listening on " + port);
            }
        });
    }
}
