package com.growdigitalbridge.gateway;

import com.growdigitalbridge.gateway.filter.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    @Test void addsCorrelationIdWhenMissing() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/info").build());
        new CorrelationIdFilter().filter(exchange, ignored -> Mono.empty()).block();
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.HEADER)).isNotBlank();
    }
}
