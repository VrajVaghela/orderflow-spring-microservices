package com.orderflow.payment.api;

import com.orderflow.payment.infrastructure.persistence.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "payments", description = "Read simulated payment results")
public class PaymentController {

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/by-order/{orderId}")
    @Operation(summary = "Read the payment recorded for an order")
    public PaymentResponse byOrder(@PathVariable UUID orderId) {
        return payments.findByOrderId(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found"));
    }
}
