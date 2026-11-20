package com.orderflow.order.api;

import com.orderflow.common.Correlation;
import com.orderflow.order.domain.IdempotencyConflictException;
import com.orderflow.order.domain.OrderNotFoundException;
import com.orderflow.order.domain.OrderValidationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderValidationException.class)
    ProblemDetail validation(OrderValidationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage());
        problem.setType(URI.create("https://orderflow.dev/problems/validation"));
        problem.setProperty("errors", ex.errors());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "request body is not valid JSON");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail missing(OrderNotFoundException ex) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Order not found", ex.getMessage());
        problem.setType(URI.create("https://orderflow.dev/problems/not-found"));
        return problem;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotency(IdempotencyConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Idempotency conflict", ex.getMessage());
        problem.setType(URI.create("https://orderflow.dev/problems/idempotency-conflict"));
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        Correlation.current().ifPresent(id -> problem.setProperty("correlationId", id));
        return problem;
    }
}
