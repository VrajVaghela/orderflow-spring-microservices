package com.orderflow.order.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResilientExecutorTest {

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void retriesUnavailableCallsThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        ResilientExecutor resilient = executor(3, Duration.ofSeconds(1), 10);

        String result = resilient.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new StatusRuntimeException(Status.UNAVAILABLE);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void timesOutACallThatDoesNotReturn() {
        ResilientExecutor resilient = executor(1, Duration.ofMillis(100), 10);

        assertThatThrownBy(() -> resilient.execute(() -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "late";
        })).isInstanceOf(InventoryCallFailedException.class)
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    @Test
    void opensTheCircuitAfterRepeatedFailures() {
        ResilientExecutor resilient = executor(1, Duration.ofSeconds(1), 4);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> resilient.execute(() -> {
                throw new StatusRuntimeException(Status.UNAVAILABLE);
            })).isInstanceOf(StatusRuntimeException.class);
        }

        assertThatThrownBy(() -> resilient.execute(() -> "should-not-run"))
                .isInstanceOf(CallNotPermittedException.class);
    }

    private ResilientExecutor executor(int attempts, Duration timeout, int window) {
        CircuitBreaker breaker = CircuitBreaker.of("inventory", CircuitBreakerConfig.custom()
                .slidingWindowSize(window)
                .minimumNumberOfCalls(window)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordExceptions(StatusRuntimeException.class, InventoryCallFailedException.class)
                .build());
        Retry retry = Retry.of("inventory", RetryConfig.custom()
                .maxAttempts(attempts)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(StatusRuntimeException.class, InventoryCallFailedException.class)
                .build());
        TimeLimiter timeLimiter = TimeLimiter.of("inventory", TimeLimiterConfig.custom()
                .timeoutDuration(timeout)
                .cancelRunningFuture(true)
                .build());
        return new ResilientExecutor(breaker, retry, timeLimiter, executor);
    }
}
