package com.orderflow.inventory.api;

import com.orderflow.common.Correlation;
import com.orderflow.inventory.domain.UnknownSkuException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownSkuException.class)
    ProblemDetail unknownSku(UnknownSkuException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Unknown SKU");
        problem.setType(URI.create("https://orderflow.dev/problems/unknown-sku"));
        Correlation.current().ifPresent(id -> problem.setProperty("correlationId", id));
        return problem;
    }
}
