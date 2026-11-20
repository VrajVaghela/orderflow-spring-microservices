package com.orderflow.inventory.infrastructure.grpc;

import com.orderflow.inventory.application.InventoryReservationService;
import com.orderflow.inventory.application.ReserveCommand;
import com.orderflow.inventory.application.ReserveOutcome;
import com.orderflow.inventory.domain.InsufficientStockException;
import com.orderflow.inventory.domain.UnknownSkuException;
import com.orderflow.inventory.v1.InventoryGrpc;
import com.orderflow.inventory.v1.ReleaseStockRequest;
import com.orderflow.inventory.v1.ReleaseStockResponse;
import com.orderflow.inventory.v1.ReserveStockRequest;
import com.orderflow.inventory.v1.ReserveStockResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class InventoryGrpcService extends InventoryGrpc.InventoryImplBase {

    private static final Logger log = LoggerFactory.getLogger(InventoryGrpcService.class);

    private final InventoryReservationService reservations;

    public InventoryGrpcService(InventoryReservationService reservations) {
        this.reservations = reservations;
    }

    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        try {
            List<ReserveCommand.Line> lines = request.getLinesList().stream()
                    .map(line -> new ReserveCommand.Line(line.getSku(), line.getQuantity()))
                    .toList();
            ReserveOutcome outcome = reservations.reserve(new ReserveCommand(request.getOrderId(), lines));
            responseObserver.onNext(ReserveStockResponse.newBuilder()
                    .setReservationId(outcome.reservationId())
                    .build());
            responseObserver.onCompleted();
        } catch (InsufficientStockException | UnknownSkuException | IllegalArgumentException ex) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            log.error("reserve failed for order {}", request.getOrderId(), ex);
            responseObserver.onError(Status.INTERNAL.withDescription("reservation failed").asRuntimeException());
        }
    }

    @Override
    public void releaseStock(ReleaseStockRequest request, StreamObserver<ReleaseStockResponse> responseObserver) {
        try {
            reservations.release(request.getOrderId());
            responseObserver.onNext(ReleaseStockResponse.newBuilder().setReleased(true).build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            log.error("release failed for order {}", request.getOrderId(), ex);
            responseObserver.onError(Status.INTERNAL.withDescription("release failed").asRuntimeException());
        }
    }
}
