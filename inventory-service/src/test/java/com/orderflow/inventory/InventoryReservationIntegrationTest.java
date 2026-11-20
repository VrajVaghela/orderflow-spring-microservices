package com.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.inventory.v1.InventoryGrpc;
import com.orderflow.inventory.v1.ReleaseStockRequest;
import com.orderflow.inventory.v1.ReserveStockRequest;
import com.orderflow.inventory.v1.StockLine;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InventoryReservationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final int GRPC_PORT = freePort();
    private static ManagedChannel channel;
    private static InventoryGrpc.InventoryBlockingStub stub;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("grpc.server.port", () -> GRPC_PORT);
    }

    @Autowired
    private TestRestTemplate http;

    @BeforeAll
    static void client() {
        channel = ManagedChannelBuilder.forAddress("localhost", GRPC_PORT).usePlaintext().build();
        stub = InventoryGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void close() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    @Test
    void reserveIsIdempotentAndReleaseRestoresStock() {
        String orderId = "order-happy";
        var first = stub.reserveStock(request(orderId, "SKU-100", 2));
        var second = stub.reserveStock(request(orderId, "SKU-100", 2));

        assertThat(second.getReservationId()).isEqualTo(first.getReservationId());
        assertThat(stock("SKU-100").available()).isEqualTo(999_998);
        assertThat(stock("SKU-100").reserved()).isEqualTo(2);

        stub.releaseStock(ReleaseStockRequest.newBuilder().setOrderId(orderId).build());
        stub.releaseStock(ReleaseStockRequest.newBuilder().setOrderId(orderId).build());

        assertThat(stock("SKU-100").available()).isEqualTo(1_000_000);
        assertThat(stock("SKU-100").reserved()).isZero();
    }

    @Test
    void insufficientStockDoesNotChangeTheRow() {
        int before = stock("SKU-LOW").available();
        StatusRuntimeException error = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class,
                () -> stub.reserveStock(request("order-low", "SKU-LOW", 2)));

        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(error.getStatus().getDescription()).contains("insufficient stock");
        assertThat(stock("SKU-LOW").available()).isEqualTo(before);
    }

    @Test
    void unknownSkuIsRejected() {
        StatusRuntimeException error = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class,
                () -> stub.reserveStock(request("order-missing", "SKU-NOPE", 1)));
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(error.getStatus().getDescription()).contains("unknown sku");
    }

    private Stock stock(String sku) {
        return http.getForObject("/api/stock/" + sku, Stock.class);
    }

    private static ReserveStockRequest request(String orderId, String sku, int quantity) {
        return ReserveStockRequest.newBuilder()
                .setOrderId(orderId)
                .addLines(StockLine.newBuilder().setSku(sku).setQuantity(quantity).build())
                .build();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    record Stock(String sku, int available, int reserved) {
    }
}
