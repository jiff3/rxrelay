package dev.rxrelay.gateway;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

final class FailOpenRateLimiter implements RateLimiter<RedisRateLimiter.Config> {
  private static final Logger LOGGER = LoggerFactory.getLogger(FailOpenRateLimiter.class);

  private final RedisRateLimiter delegate;

  FailOpenRateLimiter(RedisRateLimiter delegate) {
    this.delegate = delegate;
  }

  @Override
  public Mono<Response> isAllowed(String routeId, String id) {
    return delegate
        .isAllowed(routeId, id)
        .onErrorResume(
            exception -> {
              LOGGER.warn(
                  "Redis rate limiting unavailable for route {}; allowing request ({})",
                  routeId,
                  exception.getClass().getSimpleName());
              return Mono.just(new Response(true, Map.of()));
            });
  }

  @Override
  public Class<RedisRateLimiter.Config> getConfigClass() {
    return delegate.getConfigClass();
  }

  @Override
  public RedisRateLimiter.Config newConfig() {
    return delegate.newConfig();
  }

  @Override
  public Map<String, RedisRateLimiter.Config> getConfig() {
    return delegate.getConfig();
  }
}
