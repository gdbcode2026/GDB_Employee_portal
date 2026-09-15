package com.growdigitalbridge.gateway.filter;

import com.growdigitalbridge.platform.common.logging.SensitiveDataRedactor;
import java.util.stream.Collectors;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered; import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange; import org.springframework.web.server.WebFilter; import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingFilter implements WebFilter, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String headers = exchange.getRequest().getHeaders().entrySet().stream().map(e -> e.getKey() + "=" + SensitiveDataRedactor.headerValue(e.getKey(), String.join(",", e.getValue()))).collect(Collectors.joining(";"));
        LOG.info("request method={} path={} headers={}", exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), headers);
        return chain.filter(exchange);
    }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 1; }
}
