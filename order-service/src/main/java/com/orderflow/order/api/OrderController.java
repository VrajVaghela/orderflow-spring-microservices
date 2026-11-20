package com.orderflow.order.api;

import com.orderflow.order.application.OrderResponse;
import com.orderflow.order.application.PlaceOrderService;
import com.orderflow.order.domain.OrderNotFoundException;
import com.orderflow.order.domain.port.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "orders", description = "Place orders and read the write model")
public class OrderController {

    private final PlaceOrderService placeOrderService;
    private final OrderRepository orders;

    public OrderController(PlaceOrderService placeOrderService, OrderRepository orders) {
        this.placeOrderService = placeOrderService;
        this.orders = orders;
    }

    @PostMapping
    @Operation(summary = "Place an order", description = "Reserves stock synchronously, then requests payment asynchronously.")
    public ResponseEntity<OrderResponse> place(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PlaceOrderRequest request
    ) {
        OrderResponse response = placeOrderService.place(idempotencyKey, request.toCommand());
        HttpStatus status = "REJECTED".equals(response.status()) ? HttpStatus.CONFLICT : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one order from the write model")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @GetMapping
    @Operation(summary = "List recent orders for a customer")
    public List<OrderResponse> byCustomer(@RequestParam String customerId) {
        return orders.findByCustomerId(customerId, 50).stream().map(OrderResponse::from).toList();
    }
}
