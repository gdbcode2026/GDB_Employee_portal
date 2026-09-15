package com.growdigitalbridge.gateway.filter;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdFilter implements WebFilter, Ordered {
    public static final String HEADER = "X-Correlation-Id";
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(exchange).contextWrite(context -> context.put(HEADER, correlationId));
    }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
