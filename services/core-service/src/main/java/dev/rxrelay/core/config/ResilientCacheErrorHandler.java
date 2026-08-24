package dev.rxrelay.core.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

public class ResilientCacheErrorHandler implements CacheErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(ResilientCacheErrorHandler.class);
  private final MeterRegistry registry;

  public ResilientCacheErrorHandler(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
    warn("read", cache, exception);
  }

  @Override
  public void handleCachePutError(
      RuntimeException exception, Cache cache, Object key, Object value) {
    warn("write", cache, exception);
  }

  @Override
  public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
    warn("evict", cache, exception);
  }

  @Override
  public void handleCacheClearError(RuntimeException exception, Cache cache) {
    warn("clear", cache, exception);
  }

  private void warn(String operation, Cache cache, RuntimeException exception) {
    String cacheName = cache.getName();
    registry
        .counter("rxrelay.cache.errors", "operation", operation, "cache", cacheName)
        .increment();
    log.warn(
        "Redis cache {} failed for cache {} ({}); continuing with PostgreSQL",
        operation,
        cacheName,
        exception.getClass().getSimpleName());
  }
}
