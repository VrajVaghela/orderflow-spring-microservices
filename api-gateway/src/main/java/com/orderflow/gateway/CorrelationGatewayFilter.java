package com.orderflow.gateway;

import com.orderflow.common.Correlation;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CorrelationGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = Correlation.resolve(exchange.getRequest().getHeaders().getFirst(Correlation.HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(Correlation.HEADER, correlationId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getResponse().getHeaders().set(Correlation.HEADER, correlationId);
        return chain.filter(mutated)
                .doOnSubscribe(subscription -> Correlation.set(correlationId))
                .doFinally(signal -> Correlation.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
