package dev.rxrelay.core.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

class ResilientCacheErrorHandlerTest {
  @Test
  void applicationConfigurationSuppliesTheResilientHandlerToCachingInfrastructure() {
    CacheErrorHandler handler =
        new ApplicationConfiguration(new SimpleMeterRegistry()).errorHandler();

    org.assertj.core.api.Assertions.assertThat(handler)
        .isInstanceOf(ResilientCacheErrorHandler.class);
  }

  @Test
  void cacheFailuresNeverReplaceAuthoritativeDatabaseResults() {
    var registry = new SimpleMeterRegistry();
    var handler = new ResilientCacheErrorHandler(registry);
    Cache cache = mock(Cache.class);
    when(cache.getName()).thenReturn("medication-search");
    RuntimeException failure = new RuntimeException("redis unavailable");
    assertThatCode(
            () -> {
              handler.handleCacheGetError(failure, cache, "key");
              handler.handleCachePutError(failure, cache, "key", "value");
              handler.handleCacheEvictError(failure, cache, "key");
              handler.handleCacheClearError(failure, cache);
            })
        .doesNotThrowAnyException();
    org.assertj.core.api.Assertions.assertThat(
            registry.get("rxrelay.cache.errors").counters().stream()
                .mapToDouble(counter -> counter.count())
                .sum())
        .isEqualTo(4);
  }
}
