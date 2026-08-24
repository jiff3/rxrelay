package dev.rxrelay.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationContextTest {
  @Autowired private RateLimiter<?> rateLimiter;

  @Test
  void startsWithFailOpenRateLimiterAsPrimary() {
    assertThat(rateLimiter).isInstanceOf(FailOpenRateLimiter.class);
  }
}
