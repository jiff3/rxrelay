package dev.rxrelay.core.api;

import dev.rxrelay.core.domain.*;
import dev.rxrelay.core.service.MedicationQueryService.MedicationSnapshot;
import dev.rxrelay.core.service.OverviewQueryService;
import dev.rxrelay.core.service.OverviewQueryService.OverviewSnapshot;
import dev.rxrelay.core.service.SystemQueryService.EventFlow;
import dev.rxrelay.core.service.SystemQueryService.EventStep;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class ApiModels {
  private ApiModels() {}

  public record PageResponse<T>(
      List<T> items, int page, int size, long totalElements, int totalPages) {
    public PageResponse {
      items = List.copyOf(items);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
      return new PageResponse<>(
          page.getContent(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
  }

  public record DrugView(
      UUID id,
      String name,
      String genericName,
      String rxCui,
      String dosageForm,
      String sourceName,
      String normalizationStatus,
      List<String> shortageStatuses,
      Instant updatedAt) {
    public DrugView {
      shortageStatuses = List.copyOf(shortageStatuses);
    }

    static DrugView from(MedicationSnapshot value) {
      return new DrugView(
          value.id(),
          value.name(),
          value.genericName(),
          value.rxCui(),
          value.dosageForm(),
          value.sourceName(),
          value.normalizationStatus(),
          value.shortageStatuses(),
          value.updatedAt());
    }
  }

  public record ShortageView(
      UUID id,
      String sourceRecordId,
      String source,
      String status,
      String sourceStatus,
      String sourceUpdateType,
      String availability,
      String reason,
      String company,
      String presentation,
      String packageNdc,
      String manufacturer,
      Instant sourceUpdatedAt,
      Instant initialPostingAt,
      Instant firstSeenAt,
      Instant lastSeenAt) {
    static ShortageView from(ShortageRecord value) {
      DrugProduct product = value.getDrugProduct();
      Manufacturer maker = product == null ? null : product.getManufacturer();
      return new ShortageView(
          value.getId(),
          value.getSourceRecordId(),
          value.getSource(),
          value.getStatus().name(),
          value.getSourceStatus(),
          value.getSourceUpdateType(),
          value.getAvailability(),
          value.getReason(),
          value.getCompany(),
          value.getPresentation(),
          product == null ? null : product.getPackageNdc(),
          maker == null ? null : maker.getSourceName(),
          value.getSourceUpdatedAt(),
          value.getInitialPostingAt(),
          value.getFirstSeenAt(),
          value.getLastSeenAt());
    }
  }

  public record TimelineView(
      UUID id,
      String previousStatus,
      String newStatus,
      Instant detectedAt,
      String source,
      String eventId) {
    static TimelineView from(StatusChange value) {
      return new TimelineView(
          value.getId(),
          value.getPreviousStatus() == null ? null : value.getPreviousStatus().name(),
          value.getCurrentStatus().name(),
          value.getOccurredAt(),
          value.getShortageRecord().getSource(),
          value.getSourceEventId());
    }
  }

  public record RecentChangeView(
      UUID id,
      DrugView drug,
      String previousStatus,
      String newStatus,
      Instant occurredAt,
      String source,
      String eventId) {
    static RecentChangeView from(OverviewQueryService.RecentChangeSnapshot snapshot) {
      StatusChange value = snapshot.change();
      return new RecentChangeView(
          value.getId(),
          DrugView.from(snapshot.medication()),
          value.getPreviousStatus() == null ? null : value.getPreviousStatus().name(),
          value.getCurrentStatus().name(),
          value.getOccurredAt(),
          value.getShortageRecord().getSource(),
          value.getSourceEventId());
    }
  }

  public record CreateWatchlistRequest(@NotBlank @Size(max = 100) String name) {}

  public record AddWatchlistItemRequest(@NotNull UUID drugId) {}

  public record WatchlistItemView(UUID id, DrugView drug, Instant createdAt) {
    static WatchlistItemView from(WatchlistItem value, List<String> shortageStatuses) {
      return new WatchlistItemView(
          value.getId(),
          DrugView.from(MedicationSnapshot.from(value.getMedication(), shortageStatuses)),
          value.getCreatedAt());
    }
  }

  public record WatchlistView(
      UUID id,
      String name,
      long itemCount,
      Instant createdAt,
      Instant updatedAt,
      PageResponse<WatchlistItemView> items) {
    static WatchlistView from(Watchlist value, long itemCount, Page<WatchlistItemView> items) {
      return new WatchlistView(
          value.getId(),
          value.getName(),
          itemCount,
          value.getCreatedAt(),
          value.getUpdatedAt(),
          items == null ? null : PageResponse.from(items));
    }
  }

  public record NotificationView(
      UUID id, DrugView drug, String message, boolean read, Instant createdAt) {
    static NotificationView from(Notification value, List<String> shortageStatuses) {
      return new NotificationView(
          value.getId(),
          DrugView.from(MedicationSnapshot.from(value.getMedication(), shortageStatuses)),
          value.getMessage(),
          value.isRead(),
          value.getCreatedAt());
    }
  }

  public record IngestionRunView(
      UUID id,
      String source,
      String status,
      Integer requested,
      Integer fetched,
      Integer published,
      Integer malformed,
      Integer normalizationUnresolved,
      Integer normalizationAmbiguous,
      Integer normalizationErrors,
      Instant startedAt,
      Instant completedAt) {
    static IngestionRunView from(IngestionRun value) {
      return new IngestionRunView(
          value.getId(),
          value.getSource(),
          value.getStatus(),
          value.getRequestedCount(),
          value.getFetchedCount(),
          value.getPublishedCount(),
          value.getMalformedCount(),
          value.getNormalizationUnresolvedCount(),
          value.getNormalizationAmbiguousCount(),
          value.getNormalizationErrorCount(),
          value.getStartedAt(),
          value.getCompletedAt());
    }
  }

  public record ProcessedEventView(
      String eventId,
      String eventType,
      String schemaVersion,
      String producer,
      String correlationId,
      String processingState,
      UUID ingestionRunId,
      String sourceRecordId,
      Instant receivedAt,
      Instant processedAt,
      int retryCount,
      Instant deadLetteredAt,
      String deadLetterTopic,
      String lastErrorCode,
      String sourceTopic) {
    static ProcessedEventView from(ProcessedEvent value) {
      return new ProcessedEventView(
          value.getEventId(),
          value.getEventType(),
          value.getSchemaVersion(),
          value.getProducer(),
          value.getCorrelationId(),
          value.getProcessingState(),
          value.getIngestionRunId(),
          value.getSourceRecordId(),
          value.getReceivedAt(),
          value.getProcessedAt(),
          value.getRetryCount(),
          value.getDeadLetteredAt(),
          value.getDeadLetterTopic(),
          value.getLastErrorCode(),
          value.getSourceTopic());
    }
  }

  public record EventStepView(
      String code, String label, String state, Instant occurredAt, String detail) {
    static EventStepView from(EventStep value) {
      return new EventStepView(
          value.code(), value.label(), value.state(), value.occurredAt(), value.detail());
    }
  }

  public record EventFlowView(ProcessedEventView event, List<EventStepView> steps) {
    public EventFlowView {
      steps = List.copyOf(steps);
    }

    static EventFlowView from(EventFlow value) {
      return new EventFlowView(
          ProcessedEventView.from(value.event()),
          value.steps().stream().map(EventStepView::from).toList());
    }
  }

  public record OverviewView(
      long trackedMedications,
      long trackedShortageRecords,
      long unreadNotifications,
      List<RecentChangeView> recentChanges,
      List<DrugView> recentlyUpdatedMedications,
      IngestionRunView latestIngestionRun) {
    public OverviewView {
      recentChanges = List.copyOf(recentChanges);
      recentlyUpdatedMedications = List.copyOf(recentlyUpdatedMedications);
    }

    static OverviewView from(OverviewSnapshot value) {
      return new OverviewView(
          value.trackedMedications(),
          value.trackedShortageRecords(),
          value.unreadNotifications(),
          value.recentChanges().stream().map(RecentChangeView::from).toList(),
          value.recentlyUpdatedMedications().stream().map(DrugView::from).toList(),
          value.latestIngestionRun() == null
              ? null
              : IngestionRunView.from(value.latestIngestionRun()));
    }
  }

  public record FieldViolation(String field, String message) {}

  public record ErrorResponse(
      String code,
      String message,
      String requestId,
      Instant timestamp,
      List<FieldViolation> violations) {
    public ErrorResponse {
      violations = List.copyOf(violations);
    }
  }
}
