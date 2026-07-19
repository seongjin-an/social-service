package com.social.gateway.filter;

import io.opentelemetry.api.trace.Span;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class TraceIdResponseFilter implements WebFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            Span span = Span.current();
            if (span.getSpanContext().isValid()) {
                exchange.getResponse().getHeaders()
                        .set(TRACE_ID_HEADER, span.getSpanContext().getTraceId());
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
