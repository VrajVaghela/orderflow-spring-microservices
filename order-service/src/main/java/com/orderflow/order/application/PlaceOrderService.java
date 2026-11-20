package com.orderflow.order.application;

import com.orderflow.common.Correlation;
import com.orderflow.order.domain.IdempotencyConflictException;
import com.orderflow.order.domain.InventoryTemporarilyUnavailableException;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderNotFoundException;
import com.orderflow.order.domain.OrderValidationException;
import com.orderflow.order.domain.port.IdempotencyRecord;
import com.orderflow.order.domain.port.IdempotencyStore;
import com.orderflow.order.domain.port.InventoryReservationPort;
import com.orderflow.order.domain.port.OrderRepository;
import com.orderflow.order.domain.port.ReservationAttempt;
import com.orderflow.order.domain.port.ReserveStockCommand;
import com.orderflow.order.domain.validation.ValidationHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PlaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");

    private final ValidationHandler validation;
    private final OrderTransaction tx;
    private final InventoryReservationPort inventory;
    private final IdempotencyStore idempotency;
    private final OrderRepository orders;
    private final Supplier<UUID> ids;

    public PlaceOrderService(
            ValidationHandler validation,
            OrderTransaction tx,
            InventoryReservationPort inventory,
            IdempotencyStore idempotency,
            OrderRepository orders,
            Supplier<UUID> ids
    ) {
        this.validation = validation;
        this.tx = tx;
        this.inventory = inventory;
        this.idempotency = idempotency;
        this.orders = orders;
        this.ids = ids;
    }

    public OrderResponse place(String idempotencyKey, PlaceOrderCommand command) {
        validation.handle(command);
        String key = normalizeKey(idempotencyKey);
        String hash = hash(command);
        if (key != null) {
            var existing = idempotency.find(key);
            if (existing.isPresent()) {
                return replay(existing.get(), hash);
            }
        }
        UUID id = ids.get();
        String correlationId = Correlation.current().orElseGet(() -> UUID.randomUUID().toString());
        Order created;
        try {
            created = tx.create(id, command, correlationId, key, hash);
        } catch (DataIntegrityViolationException ex) {
            if (key == null) {
                throw ex;
            }
            IdempotencyRecord winner = idempotency.find(key)
                    .orElseThrow(() -> ex);
            return replay(winner, hash);
        }
        ReservationAttempt attempt;
        try {
            attempt = inventory.reserve(toReserve(created));
        } catch (InventoryTemporarilyUnavailableException ex) {
            log.warn("inventory unavailable for order {}; leaving it PLACED for recovery", created.id());
            return OrderResponse.from(created);
        }
        return OrderResponse.from(tx.applyReservation(created.id(), attempt));
    }

    private OrderResponse replay(IdempotencyRecord record, String hash) {
        if (!record.requestHash().equals(hash)) {
            throw new IdempotencyConflictException();
        }
        Order order = orders.findById(record.orderId())
                .orElseThrow(() -> new OrderNotFoundException(record.orderId()));
        return OrderResponse.from(order);
    }

    private static String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY.matcher(trimmed).matches()) {
            throw new OrderValidationException("Idempotency-Key must be 8-80 characters of letters, digits, '_' or '-'");
        }
        return trimmed;
    }

    static String hash(PlaceOrderCommand command) {
        String canonical = command.customerId()
                + "|" + command.currency()
                + "|" + command.paymentMethod()
                + "|" + (command.paymentToken() == null ? "" : command.paymentToken())
                + "|" + command.lines().stream()
                .map(line -> line.sku() + ":" + line.quantity() + ":" + line.unitPriceMinor())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ReserveStockCommand toReserve(Order order) {
        List<ReserveStockCommand.Line> lines = order.lines().stream()
                .map(line -> new ReserveStockCommand.Line(line.sku(), line.quantity()))
                .toList();
        return new ReserveStockCommand(order.id(), lines);
    }
}
