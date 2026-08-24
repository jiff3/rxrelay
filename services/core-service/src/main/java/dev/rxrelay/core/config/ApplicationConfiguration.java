package dev.rxrelay.core.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

@Configuration
public class ApplicationConfiguration implements CachingConfigurer {
  private final CacheErrorHandler cacheErrorHandler;

  public ApplicationConfiguration(MeterRegistry registry) {
    this.cacheErrorHandler = new ResilientCacheErrorHandler(registry);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  RedisCacheManagerBuilderCustomizer cacheCustomizer() {
    RedisCacheConfiguration defaults =
        RedisCacheConfiguration.defaultCacheConfig().disableCachingNullValues();
    return builder ->
        builder
            .enableStatistics()
            .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("medication-search", defaults.entryTtl(Duration.ofSeconds(60)))
            .withCacheConfiguration("medication-detail", defaults.entryTtl(Duration.ofMinutes(5)));
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return cacheErrorHandler;
  }
}
