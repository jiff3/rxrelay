package dev.rxrelay.gateway;

import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfiguration {
  @Bean
  KeyResolver callerKeyResolver() {
    return exchange ->
        Mono.just(
            Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse("unknown"));
  }

  @Bean
  @Primary
  RateLimiter<RedisRateLimiter.Config> failOpenRateLimiter(RedisRateLimiter redisRateLimiter) {
    return new FailOpenRateLimiter(redisRateLimiter);
  }
}
