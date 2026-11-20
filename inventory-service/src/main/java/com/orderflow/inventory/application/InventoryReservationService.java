package com.orderflow.inventory.application;

import com.orderflow.inventory.domain.InsufficientStockException;
import com.orderflow.inventory.domain.ReservationStatus;
import com.orderflow.inventory.domain.UnknownSkuException;
import com.orderflow.inventory.infrastructure.persistence.ReservationEntity;
import com.orderflow.inventory.infrastructure.persistence.ReservationLineEntity;
import com.orderflow.inventory.infrastructure.persistence.ReservationLineId;
import com.orderflow.inventory.infrastructure.persistence.ReservationLineRepository;
import com.orderflow.inventory.infrastructure.persistence.ReservationRepository;
import com.orderflow.inventory.infrastructure.persistence.StockItemEntity;
import com.orderflow.inventory.infrastructure.persistence.StockItemRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReservationService {

    private final StockItemRepository stockItems;
    private final ReservationRepository reservations;
    private final ReservationLineRepository reservationLines;

    public InventoryReservationService(
            StockItemRepository stockItems,
            ReservationRepository reservations,
            ReservationLineRepository reservationLines
    ) {
        this.stockItems = stockItems;
        this.reservations = reservations;
        this.reservationLines = reservationLines;
    }

    @Transactional
    public ReserveOutcome reserve(ReserveCommand command) {
        validate(command);
        var existing = reservations.findById(command.orderId());
        if (existing.isPresent()) {
            ReservationEntity reservation = existing.get();
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                throw new InsufficientStockException("reservation already released");
            }
            return new ReserveOutcome(reservation.getReservationId());
        }

        List<ReserveCommand.Line> sorted = command.lines().stream()
                .sorted(Comparator.comparing(ReserveCommand.Line::sku))
                .toList();
        for (ReserveCommand.Line line : sorted) {
            StockItemEntity stock = stockItems.findBySkuForUpdate(line.sku())
                    .orElseThrow(() -> new UnknownSkuException(line.sku()));
            if (stock.getAvailable() < line.quantity()) {
                throw new InsufficientStockException("insufficient stock for " + line.sku());
            }
            stock.setAvailable(stock.getAvailable() - line.quantity());
            stock.setReserved(stock.getReserved() + line.quantity());
        }

        String reservationId = UUID.randomUUID().toString();
        ReservationEntity reservation = new ReservationEntity();
        reservation.setOrderId(command.orderId());
        reservation.setReservationId(reservationId);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setCreatedAt(Instant.now());
        reservations.save(reservation);

        for (ReserveCommand.Line line : sorted) {
            ReservationLineEntity row = new ReservationLineEntity();
            row.setId(new ReservationLineId(command.orderId(), line.sku()));
            row.setQuantity(line.quantity());
            reservationLines.save(row);
        }
        return new ReserveOutcome(reservationId);
    }

    @Transactional
    public void release(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        var existing = reservations.findById(orderId);
        if (existing.isEmpty() || existing.get().getStatus() == ReservationStatus.RELEASED) {
            return;
        }
        List<ReservationLineEntity> lines = reservationLines.findByOrderId(orderId).stream()
                .sorted(Comparator.comparing(line -> line.getId().getSku()))
                .toList();
        for (ReservationLineEntity line : lines) {
            StockItemEntity stock = stockItems.findBySkuForUpdate(line.getId().getSku())
                    .orElseThrow(() -> new UnknownSkuException(line.getId().getSku()));
            stock.setAvailable(stock.getAvailable() + line.getQuantity());
            stock.setReserved(stock.getReserved() - line.getQuantity());
        }
        existing.get().setStatus(ReservationStatus.RELEASED);
    }

    private static void validate(ReserveCommand command) {
        if (command.orderId() == null || command.orderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("at least one line is required");
        }
        Set<String> seen = new HashSet<>();
        for (ReserveCommand.Line line : command.lines()) {
            if (line.sku() == null || line.sku().isBlank()) {
                throw new IllegalArgumentException("sku is required");
            }
            if (line.quantity() < 1) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            if (!seen.add(line.sku())) {
                throw new IllegalArgumentException("duplicate sku " + line.sku());
            }
        }
    }
}
