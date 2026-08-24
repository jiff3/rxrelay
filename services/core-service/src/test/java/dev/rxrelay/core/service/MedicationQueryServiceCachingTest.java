package dev.rxrelay.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.rxrelay.core.domain.Medication;
import dev.rxrelay.core.repository.MedicationRepository;
import dev.rxrelay.core.repository.ShortageRecordRepository;
import dev.rxrelay.core.repository.StatusChangeRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class MedicationQueryServiceCachingTest {
  @Test
  void identicalSearchUsesDisposableCache() {
    try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
      MedicationRepository repository = context.getBean(MedicationRepository.class);
      Medication medication =
          new Medication(
              "Fixture",
              "fixture",
              null,
              "Fixture",
              "Tablet",
              "Fixture",
              "UNRESOLVED",
              Instant.EPOCH);
      when(repository.searchFiltered(eq("fix"), isNull(), eq(""), any(Pageable.class)))
          .thenReturn(new PageImpl<>(java.util.List.of(medication)));
      MedicationQueryService service = context.getBean(MedicationQueryService.class);

      assertThat(service.search("fix", null, "", 0, 20, "name,asc").getTotalElements()).isOne();
      assertThat(service.search("fix", null, "", 0, 20, "name,asc").getTotalElements()).isOne();
      verify(repository, times(1)).searchFiltered(eq("fix"), isNull(), eq(""), any(Pageable.class));
    }
  }

  @Configuration
  @EnableCaching
  static class TestConfiguration {
    @Bean
    MedicationRepository medications() {
      return mock(MedicationRepository.class);
    }

    @Bean
    ShortageRecordRepository shortages() {
      return mock(ShortageRecordRepository.class);
    }

    @Bean
    StatusChangeRepository changes() {
      return mock(StatusChangeRepository.class);
    }

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("medication-search", "medication-detail");
    }

    @Bean
    MedicationQueryService service(
        MedicationRepository m, ShortageRecordRepository s, StatusChangeRepository c) {
      return new MedicationQueryService(m, s, c);
    }
  }
}
