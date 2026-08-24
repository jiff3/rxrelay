package dev.rxrelay.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationIdFilterTest {
  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void preservesValidCallerRequestId() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/medications").header("X-Request-Id", "trace-7"));
    WebFilterChain chain =
        current -> {
          assertThat(current.getRequest().getHeaders().getFirst("X-Request-Id"))
              .isEqualTo("trace-7");
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo("trace-7");
  }

  @Test
  void replacesUnsafeCallerRequestId() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/drugs").header("X-Request-Id", "bad value"));

    StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

    assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id"))
        .satisfies(value -> assertThat(UUID.fromString(value)).isNotNull());
  }
}
