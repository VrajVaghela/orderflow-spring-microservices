package com.orderflow.order.infrastructure.grpc;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Timeout per attempt, circuit breaker around each attempt, retry as the outer decorator.
 * Business results are returned, not thrown, so they do not open the circuit.
 */
@Component
public class ResilientExecutor {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    @Autowired
    public ResilientExecutor(
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            TimeLimiterRegistry timeLimiters
    ) {
        this.circuitBreaker = circuitBreakers.circuitBreaker("inventory");
        this.retry = retries.retry("inventory");
        this.timeLimiter = timeLimiters.timeLimiter("inventory");
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }

    ResilientExecutor(CircuitBreaker circuitBreaker, Retry retry, TimeLimiter timeLimiter, ExecutorService executor) {
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.timeLimiter = timeLimiter;
        this.executor = executor;
    }

    public <T> T execute(Supplier<T> supplier) {
        Callable<T> attempt = () -> {
            try {
                return timeLimiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(supplier, executor));
            } catch (ExecutionException ex) {
                throw unwrap(ex.getCause() == null ? ex : ex.getCause());
            } catch (TimeoutException ex) {
                throw new InventoryCallFailedException(ex);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new InventoryCallFailedException(ex);
            }
        };
        Callable<T> decorated = Retry.decorateCallable(retry, CircuitBreaker.decorateCallable(circuitBreaker, attempt));
        try {
            return decorated.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InventoryCallFailedException(ex);
        }
    }

    private static RuntimeException unwrap(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof TimeoutException timeout) {
            return new InventoryCallFailedException(timeout);
        }
        return new InventoryCallFailedException(cause);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
