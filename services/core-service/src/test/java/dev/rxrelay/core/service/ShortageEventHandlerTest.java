package dev.rxrelay.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.rxrelay.core.domain.*;
import dev.rxrelay.core.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class ShortageEventHandlerTest {
  private final MedicationRepository medications = mock(MedicationRepository.class);
  private final ManufacturerRepository manufacturers = mock(ManufacturerRepository.class);
  private final DrugProductRepository products = mock(DrugProductRepository.class);
  private final IngestionRunRepository runs = mock(IngestionRunRepository.class);
  private final ShortageRecordRepository shortages = mock(ShortageRecordRepository.class);
  private final ShortageObservationRepository observations =
      mock(ShortageObservationRepository.class);
  private final StatusChangeRepository changes = mock(StatusChangeRepository.class);
  private final WatchlistItemRepository watchlistItems = mock(WatchlistItemRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
  private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
  private final AuditEventRepository auditEvents = mock(AuditEventRepository.class);
  private final CacheManager cache = mock(CacheManager.class);
  private final Cache searchCache = mock(Cache.class);
  private final Cache detailCache = mock(Cache.class);
  private final EventJsonSerializer eventSerializer = mock(EventJsonSerializer.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC);
  private final EventMetadata metadata =
      new EventMetadata(clock.instant(), "rxrelay.shortage.observed.v1", 0, 7, 1);
  private ShortageEventHandler handler;

  @BeforeEach
  void setUp() {
    when(medications.findByRxCui(any())).thenReturn(Optional.empty());
    when(medications.findByNormalizedName(any())).thenReturn(Optional.empty());
    when(medications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(manufacturers.findByNormalizedName(any())).thenReturn(Optional.empty());
    when(manufacturers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(products.findBySourceAndSourceProductKey(any(), any())).thenReturn(Optional.empty());
    when(products.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(shortages.findBySourceRecordId(any())).thenReturn(Optional.empty());
    when(shortages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(runs.findById(any())).thenReturn(Optional.empty());
    when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(watchlistItems.findByMedicationId(any())).thenReturn(List.of());
    when(watchlistItems.findDistinctOwnerIdsByMedicationId(any())).thenReturn(List.of());
    when(notifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(processed.claim(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(1);
    when(processed.markProcessed(any(), any())).thenReturn(1);
    when(eventSerializer.serialize(any())).thenReturn("{}");
    when(cache.getCache("medication-search")).thenReturn(searchCache);
    when(cache.getCache("medication-detail")).thenReturn(detailCache);
    handler =
        new ShortageEventHandler(
            medications,
            manufacturers,
            products,
            runs,
            shortages,
            observations,
            changes,
            watchlistItems,
            notifications,
            processed,
            outbox,
            auditEvents,
            cache,
            eventSerializer,
            "rxrelay.availability.changed.v1",
            "rxrelay.notification.created.v1",
            clock);
  }

  @Test
  void newObservationPersistsProvenanceAndOneDomainChange() {
    handler.handle(event("11111111-1111-4111-8111-111111111111", "state-1"), metadata);
    verify(shortages).save(any());
    verify(observations).save(any());
    verify(changes).save(any());
    verify(outbox).save(any());
    verify(auditEvents).save(any());
    verify(processed).markProcessed(any(), any());
    verify(searchCache).clear();
    verify(detailCache).clear();
  }

  @Test
  void duplicateDeliveryIsIgnored() {
    when(processed.claim(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(0);
    handler.handle(event("11111111-1111-4111-8111-111111111111", "state-1"), metadata);
    verifyNoInteractions(shortages, observations, changes, outbox);
  }

  @Test
  void unchangedLaterRunStoresObservationWithoutAnotherChange() {
    IngestionEvent first = event("11111111-1111-4111-8111-111111111111", "state-1");
    IngestionRun run =
        new IngestionRun(UUID.fromString(first.ingestionRunId()), "openfda", first.occurredAt());
    Medication medication =
        new Medication(
            "Fixture",
            "fixture",
            "123",
            "Fixture",
            "Injection",
            "Fixture",
            "RESOLVED",
            clock.instant());
    Manufacturer manufacturer = new Manufacturer("Fixture Co", "fixture co", clock.instant());
    DrugProduct product =
        new DrugProduct(
            medication,
            manufacturer,
            "openfda",
            "source-1",
            first.payload().sourceValues(),
            clock.instant());
    ShortageRecord existing =
        new ShortageRecord("source-1", medication, product, run, first, clock.instant());
    when(shortages.findBySourceRecordId("source-1")).thenReturn(Optional.of(existing));

    handler.handle(event("22222222-2222-4222-8222-222222222222", "state-1"), metadata);

    verify(observations).save(any());
    verifyNoInteractions(changes, outbox);
  }

  @Test
  void sameEventDeliveredThreeTimesCreatesOneTransition() {
    when(processed.claim(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(1, 0, 0);
    IngestionEvent value = event("11111111-1111-4111-8111-111111111111", "state-1");

    handler.handle(value, metadata);
    handler.handle(value, metadata);
    handler.handle(value, metadata);

    verify(changes, times(1)).save(any());
    verify(outbox, times(1)).save(any());
  }

  @Test
  void watchlistedDrugCreatesOneLogicalNotificationAndEvent() {
    when(watchlistItems.findDistinctOwnerIdsByMedicationId(any()))
        .thenReturn(List.of("demo", "demo"));

    handler.handle(event("11111111-1111-4111-8111-111111111111", "state-1"), metadata);

    verify(notifications, times(1)).save(any());
    verify(outbox, times(2)).save(any());
  }

  @Test
  void unrelatedDrugCreatesNoNotification() {
    handler.handle(event("11111111-1111-4111-8111-111111111111", "state-1"), metadata);
    verifyNoInteractions(notifications);
  }

  @Test
  void redisFailureDoesNotPreventAuthoritativeStateOrEventCommit() {
    doThrow(new IllegalStateException("synthetic Redis outage")).when(searchCache).clear();

    EventProcessingResult result =
        handler.handle(event("11111111-1111-4111-8111-111111111111", "state-1"), metadata);

    org.assertj.core.api.Assertions.assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
    verify(shortages).save(any());
    verify(changes).save(any());
    verify(outbox).save(any());
    verify(processed).markProcessed(any(), any());
    verify(detailCache).clear();
  }

  private IngestionEvent event(String eventId, String fingerprint) {
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    IngestionEvent.SourceValues values =
        new IngestionEvent.SourceValues(
            "12345-678-90",
            "Fixture",
            "Fixture Co",
            "Fixture vial",
            "Reverified",
            "Available",
            null,
            null,
            null,
            null,
            List.of("Test fixture"),
            "Injection",
            "Current",
            now,
            null,
            null,
            now);
    IngestionEvent.ObservationPayload payload =
        new IngestionEvent.ObservationPayload(
            "source-1",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            fingerprint,
            "fixture",
            "Fixture",
            "123",
            "RESOLVED",
            "Fixture",
            "CURRENT",
            values);
    return new IngestionEvent(
        "1.1",
        eventId,
        "ShortageObserved",
        now,
        "00000000-0000-0000-0000-000000000001",
        "rxrelay-ingestion",
        "openfda",
        "00000000-0000-0000-0000-000000000001",
        null,
        null,
        payload);
  }
}
