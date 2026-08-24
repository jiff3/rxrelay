package dev.rxrelay.gateway;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdFilter implements WebFilter, Ordered {
  static final String HEADER = "X-Request-Id";
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId =
        Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(HEADER))
            .filter(value -> SAFE_REQUEST_ID.matcher(value).matches())
            .orElseGet(() -> UUID.randomUUID().toString());
    ServerHttpRequest request =
        exchange.getRequest().mutate().headers(headers -> headers.set(HEADER, requestId)).build();
    exchange.getResponse().getHeaders().set(HEADER, requestId);
    return chain.filter(exchange.mutate().request(request).build());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
