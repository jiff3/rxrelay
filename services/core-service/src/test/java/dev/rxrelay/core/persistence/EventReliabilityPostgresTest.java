package dev.rxrelay.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rxrelay.core.domain.IngestionEvent;
import dev.rxrelay.core.domain.Medication;
import dev.rxrelay.core.domain.Watchlist;
import dev.rxrelay.core.domain.WatchlistItem;
import dev.rxrelay.core.repository.*;
import dev.rxrelay.core.service.EventMetadata;
import dev.rxrelay.core.service.EventProcessingResult;
import dev.rxrelay.core.service.ShortageEventHandler;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.cache.type=simple",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.auto-create=false",
      "rxrelay.outbox.poll-delay-ms=3600000"
    })
class EventReliabilityPostgresTest {
  private static final EmbeddedPostgres POSTGRES = startPostgres();

  @Autowired ShortageEventHandler handler;
  @Autowired MedicationRepository medications;
  @Autowired WatchlistRepository watchlists;
  @Autowired WatchlistItemRepository watchlistItems;
  @Autowired ProcessedEventRepository processedEvents;
  @Autowired StatusChangeRepository changes;
  @Autowired NotificationRepository notifications;
  @Autowired ShortageObservationRepository observations;
  @Autowired ShortageRecordRepository shortages;
  @Autowired OutboxEventRepository outbox;
  @Autowired DrugProductRepository products;
  @Autowired ManufacturerRepository manufacturers;
  @Autowired IngestionRunRepository runs;
  @Autowired AuditEventRepository audits;
  @Autowired CacheManager cacheManager;
  @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

  private long offset;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", EventReliabilityPostgresTest::url);
    properties.add("spring.datasource.username", () -> "postgres");
    properties.add("spring.datasource.password", () -> "");
  }

  @BeforeEach
  void reset() {
    notifications.deleteAll();
    watchlistItems.deleteAll();
    watchlists.deleteAll();
    processedEvents.deleteAll();
    audits.deleteAll();
    changes.deleteAll();
    observations.deleteAll();
    shortages.deleteAll();
    outbox.deleteAll();
    products.deleteAll();
    manufacturers.deleteAll();
    runs.deleteAll();
    medications.deleteAll();
    offset = 0;
  }

  @Test
  void duplicatesProduceOneTransitionAndOneLogicalNotification() {
    IngestionEvent baseline =
        event(
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
            "source-watched",
            "a".repeat(64),
            "CURRENT",
            Instant.parse("2026-08-20T00:00:00Z"),
            "Reliability Fixture");
    assertThat(handle(baseline)).isEqualTo(EventProcessingResult.PROCESSED);
    assertThat(handle(baseline)).isEqualTo(EventProcessingResult.DUPLICATE);
    assertThat(handle(baseline)).isEqualTo(EventProcessingResult.DUPLICATE);
    assertThat(changes.count()).isOne();

    Medication medication = medications.findByNormalizedName("reliability fixture").orElseThrow();
    Watchlist first = watchlists.save(new Watchlist("demo", "Critical", Instant.now()));
    Watchlist second = watchlists.save(new Watchlist("demo", "Secondary", Instant.now()));
    watchlistItems.save(new WatchlistItem(first, medication, Instant.now()));
    watchlistItems.save(new WatchlistItem(second, medication, Instant.now()));
    cacheManager.getCache("medication-search").put("fixture", "stale");
    cacheManager.getCache("medication-detail").put(medication.getId(), "stale");

    IngestionEvent changed =
        event(
            "33333333-3333-4333-8333-333333333333",
            "44444444-4444-4444-8444-444444444444",
            "source-watched",
            "b".repeat(64),
            "RESOLVED",
            Instant.parse("2026-08-21T00:00:00Z"),
            "Reliability Fixture");
    assertThat(handle(changed)).isEqualTo(EventProcessingResult.PROCESSED);
    assertThat(handle(changed)).isEqualTo(EventProcessingResult.DUPLICATE);
    assertThat(handle(changed)).isEqualTo(EventProcessingResult.DUPLICATE);

    assertThat(changes.count()).isEqualTo(2);
    assertThat(notifications.count()).isOne();
    assertThat(outbox.count()).isEqualTo(3);
    assertThat(processedEvents.count()).isEqualTo(2);
    assertThat(cacheManager.getCache("medication-search").get("fixture")).isNull();
    assertThat(cacheManager.getCache("medication-detail").get(medication.getId())).isNull();

    IngestionEvent unrelated =
        event(
            "55555555-5555-4555-8555-555555555555",
            "66666666-6666-4666-8666-666666666666",
            "source-unrelated",
            "c".repeat(64),
            "CURRENT",
            Instant.parse("2026-08-22T00:00:00Z"),
            "Unrelated Fixture");
    handle(unrelated);
    assertThat(notifications.count()).isOne();
  }

  @Test
  void olderSourceUpdateIsEvidenceButCannotReplaceCurrentState() {
    IngestionEvent current =
        event(
            "77777777-7777-4777-8777-777777777777",
            "88888888-8888-4888-8888-888888888888",
            "source-order",
            "d".repeat(64),
            "RESOLVED",
            Instant.parse("2026-08-22T00:00:00Z"),
            "Ordered Fixture");
    handle(current);
    IngestionEvent stale =
        event(
            "99999999-9999-4999-8999-999999999999",
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "source-order",
            "e".repeat(64),
            "CURRENT",
            Instant.parse("2026-08-01T00:00:00Z"),
            "Ordered Fixture");

    assertThat(handle(stale)).isEqualTo(EventProcessingResult.STALE_OBSERVATION);
    assertThat(shortages.findBySourceRecordId("source-order").orElseThrow().getStatus().name())
        .isEqualTo("RESOLVED");
    assertThat(observations.count()).isEqualTo(2);
    assertThat(changes.count()).isOne();
  }

  @Test
  void databaseFailureRollsBackEventClaimAndDomainWrites() {
    String oversizedName = "x".repeat(301);
    IngestionEvent invalidForDatabase =
        event(
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
            "source-rollback",
            "f".repeat(64),
            "CURRENT",
            Instant.parse("2026-08-22T00:00:00Z"),
            oversizedName);

    assertThatThrownBy(() -> handle(invalidForDatabase))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(processedEvents.existsById(invalidForDatabase.eventId())).isFalse();
    assertThat(shortages.findBySourceRecordId("source-rollback")).isEmpty();
  }

  private EventProcessingResult handle(IngestionEvent event) {
    return handler.handle(
        event, new EventMetadata(Instant.now(), "rxrelay.shortage.observed.v1", 0, offset++, 1));
  }

  private IngestionEvent event(
      String eventId,
      String runId,
      String sourceRecordId,
      String fingerprint,
      String normalizedStatus,
      Instant sourceDate,
      String name) {
    IngestionEvent.SourceValues values =
        new IngestionEvent.SourceValues(
            "0000-0000-00",
            name,
            "Synthetic Fixture Company",
            "Synthetic fixture presentation",
            "Synthetic",
            "Synthetic fixture availability",
            null,
            null,
            null,
            "Synthetic fixture reason",
            List.of("Synthetic fixture"),
            "Test fixture",
            normalizedStatus,
            sourceDate,
            null,
            null,
            Instant.parse("2026-08-01T00:00:00Z"));
    IngestionEvent.ObservationPayload payload =
        new IngestionEvent.ObservationPayload(
            sourceRecordId,
            fingerprint,
            fingerprint,
            name.toLowerCase(),
            name,
            null,
            "SKIPPED",
            name,
            normalizedStatus,
            values);
    return new IngestionEvent(
        "1.1",
        eventId,
        "ShortageObserved",
        sourceDate,
        runId,
        "rxrelay-ingestion",
        "openfda",
        runId,
        null,
        null,
        payload);
  }

  @AfterAll
  static void closePostgres() throws IOException {
    POSTGRES.close();
  }

  private static EmbeddedPostgres startPostgres() {
    try {
      return EmbeddedPostgres.builder().start();
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static String url() {
    return POSTGRES.getJdbcUrl("postgres", "postgres");
  }
}
