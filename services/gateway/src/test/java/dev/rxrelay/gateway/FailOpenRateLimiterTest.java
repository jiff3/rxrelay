package dev.rxrelay.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class FailOpenRateLimiterTest {
  @Test
  void allowsRequestWhenRedisLimiterFails() {
    RedisRateLimiter delegate = mock(RedisRateLimiter.class);
    when(delegate.isAllowed("core-api", "127.0.0.1"))
        .thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));

    StepVerifier.create(new FailOpenRateLimiter(delegate).isAllowed("core-api", "127.0.0.1"))
        .assertNext(
            response -> {
              assertThat(response.isAllowed()).isTrue();
              assertThat(response.getHeaders()).isEmpty();
            })
        .verifyComplete();
  }
}
