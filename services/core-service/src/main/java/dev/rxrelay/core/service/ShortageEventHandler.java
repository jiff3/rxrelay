package dev.rxrelay.core.service;

import dev.rxrelay.core.domain.*;
import dev.rxrelay.core.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortageEventHandler {
  private static final Logger log = LoggerFactory.getLogger(ShortageEventHandler.class);

  private final MedicationRepository medications;
  private final ManufacturerRepository manufacturers;
  private final DrugProductRepository products;
  private final IngestionRunRepository runs;
  private final ShortageRecordRepository shortages;
  private final ShortageObservationRepository observations;
  private final StatusChangeRepository changes;
  private final WatchlistItemRepository watchlistItems;
  private final NotificationRepository notifications;
  private final ProcessedEventRepository processedEvents;
  private final OutboxEventRepository outbox;
  private final AuditEventRepository auditEvents;
  private final CacheManager cacheManager;
  private final EventJsonSerializer eventSerializer;
  private final String availabilityTopic;
  private final String notificationTopic;
  private final Clock clock;

  public ShortageEventHandler(
      MedicationRepository medications,
      ManufacturerRepository manufacturers,
      DrugProductRepository products,
      IngestionRunRepository runs,
      ShortageRecordRepository shortages,
      ShortageObservationRepository observations,
      StatusChangeRepository changes,
      WatchlistItemRepository watchlistItems,
      NotificationRepository notifications,
      ProcessedEventRepository processedEvents,
      OutboxEventRepository outbox,
      AuditEventRepository auditEvents,
      CacheManager cacheManager,
      EventJsonSerializer eventSerializer,
      @Value("${rxrelay.kafka.availability-topic}") String availabilityTopic,
      @Value("${rxrelay.kafka.notification-topic}") String notificationTopic,
      Clock clock) {
    this.medications = medications;
    this.manufacturers = manufacturers;
    this.products = products;
    this.runs = runs;
    this.shortages = shortages;
    this.observations = observations;
    this.changes = changes;
    this.watchlistItems = watchlistItems;
    this.notifications = notifications;
    this.processedEvents = processedEvents;
    this.outbox = outbox;
    this.auditEvents = auditEvents;
    this.cacheManager = cacheManager;
    this.eventSerializer = eventSerializer;
    this.availabilityTopic = availabilityTopic;
    this.notificationTopic = notificationTopic;
    this.clock = clock;
  }

  @Transactional
  public EventProcessingResult handle(IngestionEvent event, EventMetadata metadata) {
    UUID runId = UUID.fromString(event.ingestionRunId());
    Instant now = clock.instant();
    // Run lifecycle and observation events use different Kafka keys, so an observation may be
    // delivered first. Materialize the referenced run in this same transaction before claiming
    // the event; a later RunStarted event simply observes the existing row.
    IngestionRun run =
        runs.findById(runId)
            .orElseGet(
                () -> runs.save(new IngestionRun(runId, event.source(), event.occurredAt())));
    String sourceRecordId = event.payload() == null ? null : event.payload().sourceRecordId();
    int claimed =
        processedEvents.claim(
            event.eventId(),
            event.eventType(),
            runId,
            sourceRecordId,
            event.schemaVersion(),
            event.producer(),
            event.correlationId(),
            metadata.receivedAt(),
            metadata.topic(),
            metadata.partition(),
            metadata.offset());
    if (claimed == 0) return EventProcessingResult.DUPLICATE;

    EventProcessingResult result;
    switch (event.eventType()) {
      case "IngestionRunStarted" -> {
        result = EventProcessingResult.PROCESSED;
      }
      case "IngestionRunCompleted" -> {
        completeRun(event, run);
        result = EventProcessingResult.PROCESSED;
      }
      case "ShortageObserved" -> result = observe(event, run, now);
      default -> throw new IllegalArgumentException("Unsupported event type: " + event.eventType());
    }
    if (processedEvents.markProcessed(event.eventId(), now) != 1) {
      throw new IllegalStateException("Could not finalize event processing marker");
    }
    return result;
  }

  private void completeRun(IngestionEvent event, IngestionRun run) {
    if (event.summary() == null || blank(event.runStatus())) {
      throw new IllegalArgumentException("Completed run event requires status and summary");
    }
    run.complete(event.runStatus(), event.summary(), event.occurredAt());
    runs.save(run);
    auditEvents.save(
        new AuditEvent(
            "IngestionRunCompleted",
            run,
            null,
            "Run completed with status " + event.runStatus(),
            event.occurredAt()));
  }

  private EventProcessingResult observe(IngestionEvent event, IngestionRun run, Instant now) {
    IngestionEvent.ObservationPayload payload = event.payload();
    IngestionEvent.SourceValues values = payload.sourceValues();
    ShortageRecord record = shortages.findBySourceRecordId(payload.sourceRecordId()).orElse(null);
    if (record != null && record.isOlderThanCurrent(values.updateDate())) {
      observations.save(
          new ShortageObservation(
              event.eventId(),
              record,
              run,
              payload.sourcePayloadHash(),
              payload.stateFingerprint(),
              event.occurredAt()));
      auditEvents.save(
          new AuditEvent(
              "StaleShortageObservationIgnored",
              run,
              payload.sourceRecordId(),
              "Older source update retained as provenance without replacing current state",
              now));
      return EventProcessingResult.STALE_OBSERVATION;
    }

    Medication medication = resolveMedication(payload, values, now);
    Manufacturer manufacturer = resolveManufacturer(values.companyName(), now);
    DrugProduct product =
        products
            .findBySourceAndSourceProductKey(event.source(), payload.sourceRecordId())
            .orElse(null);
    if (product == null) {
      product =
          new DrugProduct(
              medication, manufacturer, event.source(), payload.sourceRecordId(), values, now);
    } else {
      product.apply(medication, manufacturer, values, now);
    }
    product = products.save(product);

    String previousFingerprint = record == null ? null : record.getStateFingerprint();
    ShortageStatus previousStatus = record == null ? null : record.getStatus();
    boolean meaningfulChange =
        record == null || !payload.stateFingerprint().equals(previousFingerprint);
    if (record == null) {
      record = new ShortageRecord(payload.sourceRecordId(), medication, product, run, event, now);
    } else {
      record.reassociate(medication, product);
      record.apply(run, event, now);
    }
    record = shortages.save(record);
    observations.save(
        new ShortageObservation(
            event.eventId(),
            record,
            run,
            payload.sourcePayloadHash(),
            payload.stateFingerprint(),
            event.occurredAt()));

    if (meaningfulChange) {
      UUID domainEventId =
          UUID.nameUUIDFromBytes(
              (event.source()
                      + ":"
                      + run.getId()
                      + ":"
                      + payload.sourceRecordId()
                      + ":"
                      + previousFingerprint
                      + ":"
                      + payload.stateFingerprint())
                  .getBytes(StandardCharsets.UTF_8));
      changes.save(
          new StatusChange(
              record,
              previousStatus,
              record.getStatus(),
              event.occurredAt(),
              domainEventId.toString(),
              run,
              previousFingerprint,
              payload.stateFingerprint()));
      enqueueAvailabilityEvent(
          domainEventId,
          event,
          medication,
          previousStatus,
          record.getStatus(),
          values.updateDate(),
          now);
      notifyWatchers(
          domainEventId,
          UUID.fromString(event.correlationId()),
          medication,
          previousStatus,
          record.getStatus(),
          now);
      auditEvents.save(
          new AuditEvent(
              "ShortageStateChanged",
              run,
              payload.sourceRecordId(),
              (previousStatus == null ? "NEW" : previousStatus.name())
                  + " -> "
                  + record.getStatus().name(),
              now));
      clearCache("medication-search");
      clearCache("medication-detail");
    }
    return EventProcessingResult.PROCESSED;
  }

  private Medication resolveMedication(
      IngestionEvent.ObservationPayload payload, IngestionEvent.SourceValues values, Instant now) {
    Medication medication = null;
    if (!blank(payload.rxCui())) medication = medications.findByRxCui(payload.rxCui()).orElse(null);
    if (medication == null)
      medication =
          medications.findByNormalizedName(normalizeText(values.genericName())).orElse(null);
    if (medication == null)
      medication = medications.findByNormalizedName(payload.normalizedName()).orElse(null);
    if (medication == null) {
      medication =
          new Medication(
              payload.canonicalName(),
              payload.normalizedName(),
              blankToNull(payload.rxCui()),
              values.genericName(),
              values.dosageForm(),
              values.genericName(),
              payload.normalizationStatus(),
              now);
    } else {
      medication.refresh(
          payload.canonicalName(),
          blankToNull(payload.rxCui()),
          values.genericName(),
          values.dosageForm(),
          values.genericName(),
          payload.normalizationStatus(),
          now);
    }
    return medications.save(medication);
  }

  private Manufacturer resolveManufacturer(String sourceName, Instant now) {
    String normalized = normalizeText(sourceName);
    return manufacturers
        .findByNormalizedName(normalized)
        .orElseGet(() -> manufacturers.save(new Manufacturer(sourceName, normalized, now)));
  }

  private void enqueueAvailabilityEvent(
      UUID eventId,
      IngestionEvent sourceEvent,
      Medication medication,
      ShortageStatus previous,
      ShortageStatus current,
      Instant sourceUpdatedAt,
      Instant now) {
    OutboundEvents.DrugAvailabilityChanged value =
        new OutboundEvents.DrugAvailabilityChanged(
            "1.1",
            eventId,
            "DrugAvailabilityChanged",
            now,
            UUID.fromString(sourceEvent.correlationId()),
            "rxrelay-core",
            sourceEvent.source(),
            sourceEvent.payload().sourceRecordId(),
            UUID.fromString(sourceEvent.ingestionRunId()),
            medication.getId(),
            previous == null ? null : previous.name(),
            current.name(),
            now,
            sourceUpdatedAt);
    outbox.save(
        new OutboxEvent(
            eventId,
            availabilityTopic,
            sourceEvent.payload().sourceRecordId(),
            "DrugAvailabilityChanged",
            eventSerializer.serialize(value),
            sourceEvent.correlationId(),
            now));
  }

  private void notifyWatchers(
      UUID availabilityEventId,
      UUID correlationId,
      Medication medication,
      ShortageStatus previous,
      ShortageStatus current,
      Instant now) {
    String transition =
        previous == null
            ? "is now tracked as " + current
            : "changed from " + previous + " to " + current;
    for (String ownerId :
        watchlistItems.findDistinctOwnerIdsByMedicationId(medication.getId()).stream()
            .distinct()
            .toList()) {
      UUID notificationId =
          UUID.nameUUIDFromBytes(
              ("notification:" + availabilityEventId + ":" + ownerId)
                  .getBytes(StandardCharsets.UTF_8));
      Notification saved =
          notifications.save(
              new Notification(
                  notificationId,
                  ownerId,
                  medication,
                  medication.getCanonicalName() + " " + transition,
                  availabilityEventId.toString(),
                  correlationId.toString(),
                  now));
      UUID notificationEventId =
          UUID.nameUUIDFromBytes(
              ("notification-created:" + notificationId).getBytes(StandardCharsets.UTF_8));
      OutboundEvents.NotificationCreated notificationEvent =
          new OutboundEvents.NotificationCreated(
              "1.0",
              notificationEventId,
              "NotificationCreated",
              now,
              correlationId,
              "rxrelay-core",
              new OutboundEvents.NotificationPayload(
                  notificationId, ownerId, medication.getId(), availabilityEventId, now));
      outbox.save(
          new OutboxEvent(
              notificationEventId,
              notificationTopic,
              ownerId,
              "NotificationCreated",
              eventSerializer.serialize(notificationEvent),
              correlationId.toString(),
              now));
      auditEvents.save(
          AuditEvent.applicationEvent(
              "NotificationGenerated",
              "NOTIFICATION",
              saved.getId(),
              "Supply-state notification generated",
              now));
    }
  }

  private void clearCache(String name) {
    try {
      var cache = cacheManager.getCache(name);
      if (cache != null) cache.clear();
    } catch (RuntimeException exception) {
      log.atWarn()
          .addKeyValue("cache", name)
          .addKeyValue("failureType", exception.getClass().getSimpleName())
          .log(
              "Cache invalidation failed; PostgreSQL state remains authoritative and the cache TTL bounds staleness");
    }
  }

  private static String normalizeText(String value) {
    return value.strip().toLowerCase().replaceAll("[^a-z0-9]+", " ").strip();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String blankToNull(String value) {
    return blank(value) ? null : value;
  }
}
