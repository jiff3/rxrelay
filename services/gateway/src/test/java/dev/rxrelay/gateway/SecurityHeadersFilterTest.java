package dev.rxrelay.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SecurityHeadersFilterTest {
  @Test
  void addsBaselineHeadersToApiResponses() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/drugs"));

    StepVerifier.create(new SecurityHeadersFilter().filter(exchange, ignored -> Mono.empty()))
        .verifyComplete();

    assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
        .isEqualTo("nosniff");
    assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
    assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"))
        .contains("frame-ancestors 'none'");
  }
}
