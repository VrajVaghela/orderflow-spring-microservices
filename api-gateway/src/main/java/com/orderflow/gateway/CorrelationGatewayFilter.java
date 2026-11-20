package com.orderflow.gateway;

import com.orderflow.common.Correlation;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Runs for every request, including actuator. A gateway GlobalFilter would
 * only see routes that the gateway itself proxies.
 */
@Component
public class CorrelationGatewayFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = Correlation.resolve(exchange.getRequest().getHeaders().getFirst(Correlation.HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(Correlation.HEADER, correlationId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(Correlation.HEADER, correlationId);
            return Mono.empty();
        });
        return chain.filter(mutated)
                .doOnSubscribe(subscription -> Correlation.set(correlationId))
                .doFinally(signal -> Correlation.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
