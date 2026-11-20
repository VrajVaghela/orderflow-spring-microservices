package com.orderflow.order.infrastructure.grpc;

import com.orderflow.inventory.v1.InventoryGrpc;
import com.orderflow.inventory.v1.ReleaseStockRequest;
import com.orderflow.inventory.v1.ReserveStockRequest;
import com.orderflow.inventory.v1.ReserveStockResponse;
import com.orderflow.inventory.v1.StockLine;
import com.orderflow.order.config.InventoryClientProperties;
import com.orderflow.order.domain.InventoryTemporarilyUnavailableException;
import com.orderflow.order.domain.port.InventoryReservationPort;
import com.orderflow.order.domain.port.ReservationAttempt;
import com.orderflow.order.domain.port.ReserveStockCommand;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Adapter: the domain talks to {@link InventoryReservationPort}.
 * This class is the only place that knows the gRPC messages.
 */
@Component
public class InventoryGrpcAdapter implements InventoryReservationPort {

    private final ManagedChannel channel;
    private final InventoryGrpc.InventoryBlockingStub stub;
    private final ResilientExecutor resilient;
    private final InventoryClientProperties properties;

    public InventoryGrpcAdapter(InventoryClientProperties properties, ResilientExecutor resilient) {
        this.properties = properties;
        this.resilient = resilient;
        this.channel = NettyChannelBuilder.forAddress(properties.host(), properties.port())
                .usePlaintext()
                .intercept(new CorrelationClientInterceptor())
                .build();
        this.stub = InventoryGrpc.newBlockingStub(channel);
    }

    @Override
    public ReservationAttempt reserve(ReserveStockCommand command) {
        try {
            Raw raw = resilient.execute(() -> callReserve(command));
            if (raw.rejected()) {
                return ReservationAttempt.rejected(raw.message());
            }
            return ReservationAttempt.reserved(raw.reservationId());
        } catch (CallNotPermittedException | StatusRuntimeException | InventoryCallFailedException ex) {
            throw new InventoryTemporarilyUnavailableException("inventory unavailable", ex);
        }
    }

    @Override
    public void release(UUID orderId) {
        try {
            resilient.execute(() -> {
                callRelease(orderId);
                return Boolean.TRUE;
            });
        } catch (CallNotPermittedException | StatusRuntimeException | InventoryCallFailedException ex) {
            throw new InventoryTemporarilyUnavailableException("inventory unavailable", ex);
        }
    }

    private Raw callReserve(ReserveStockCommand command) {
        ReserveStockRequest.Builder builder = ReserveStockRequest.newBuilder()
                .setOrderId(command.orderId().toString());
        for (ReserveStockCommand.Line line : command.lines()) {
            builder.addLines(StockLine.newBuilder().setSku(line.sku()).setQuantity(line.quantity()).build());
        }
        try {
            ReserveStockResponse response = stub
                    .withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS)
                    .reserveStock(builder.build());
            return Raw.ok(response.getReservationId());
        } catch (StatusRuntimeException ex) {
            if (isBusiness(ex)) {
                String description = ex.getStatus().getDescription();
                return Raw.rejected(description == null ? "reservation rejected" : description);
            }
            throw ex;
        }
    }

    private void callRelease(UUID orderId) {
        try {
            stub.withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS)
                    .releaseStock(ReleaseStockRequest.newBuilder().setOrderId(orderId.toString()).build());
        } catch (StatusRuntimeException ex) {
            if (isBusiness(ex)) {
                return;
            }
            throw ex;
        }
    }

    private static boolean isBusiness(StatusRuntimeException ex) {
        Status.Code code = ex.getStatus().getCode();
        return code == Status.Code.FAILED_PRECONDITION || code == Status.Code.INVALID_ARGUMENT;
    }

    @PreDestroy
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(2, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    private record Raw(boolean rejected, String reservationId, String message) {
        static Raw ok(String reservationId) {
            return new Raw(false, reservationId, null);
        }

        static Raw rejected(String message) {
            return new Raw(true, null, message);
        }
    }
}
