package dev.rxrelay.core.service;

import dev.rxrelay.core.domain.IngestionRun;
import dev.rxrelay.core.domain.Notification;
import dev.rxrelay.core.domain.ProcessedEvent;
import dev.rxrelay.core.domain.StatusChange;
import dev.rxrelay.core.repository.IngestionRunRepository;
import dev.rxrelay.core.repository.NotificationRepository;
import dev.rxrelay.core.repository.ProcessedEventRepository;
import dev.rxrelay.core.repository.StatusChangeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SystemQueryService {
  private final IngestionRunRepository runs;
  private final ProcessedEventRepository events;
  private final StatusChangeRepository changes;
  private final NotificationRepository notifications;

  public SystemQueryService(
      IngestionRunRepository runs,
      ProcessedEventRepository events,
      StatusChangeRepository changes,
      NotificationRepository notifications) {
    this.runs = runs;
    this.events = events;
    this.changes = changes;
    this.notifications = notifications;
  }

  public Page<IngestionRun> runs(int page, int size) {
    return runs.findAllBy(
        PageRequest.of(
            page, Math.min(size, 50), Sort.by(Sort.Order.desc("startedAt"), Sort.Order.asc("id"))));
  }

  public ProcessedEvent event(String id) {
    return events.findById(id).orElseThrow(() -> new NotFoundException("Event not found"));
  }

  public EventFlow eventFlow(String id, String ownerId) {
    ProcessedEvent event = event(id);
    List<EventStep> steps = new ArrayList<>();
    steps.add(
        new EventStep(
            "RECEIVED",
            "Received from Kafka",
            "COMPLETE",
            event.getReceivedAt(),
            event.getSourceTopic()));
    if (event.getRetryCount() > 0) {
      steps.add(
          new EventStep(
              "RETRIED",
              "Delivery retried",
              "COMPLETE",
              null,
              event.getRetryCount() + " recorded retry attempts"));
    }
    StatusChange change = findChange(event);
    if (change != null) {
      steps.add(
          new EventStep(
              "TRANSITION_PERSISTED",
              "Availability transition persisted",
              "COMPLETE",
              change.getOccurredAt(),
              change.getSourceEventId()));
      Notification notification =
          notifications
              .findFirstByOwnerIdAndSourceEventIdOrderByCreatedAtAsc(
                  ownerId, change.getSourceEventId())
              .orElse(null);
      if (notification != null) {
        steps.add(
            new EventStep(
                "NOTIFICATION_CREATED",
                "Watchlist notification created",
                "COMPLETE",
                notification.getCreatedAt(),
                notification.getId().toString()));
      }
    }
    if (event.getProcessedAt() != null) {
      steps.add(
          new EventStep(
              "PROCESSED",
              "Processing transaction committed",
              "COMPLETE",
              event.getProcessedAt(),
              event.getProcessingState()));
    }
    if (event.getDeadLetteredAt() != null) {
      steps.add(
          new EventStep(
              "DEAD_LETTERED",
              "Published to dead-letter topic",
              "FAILED",
              event.getDeadLetteredAt(),
              event.getDeadLetterTopic()));
    }
    return new EventFlow(event, List.copyOf(steps));
  }

  private StatusChange findChange(ProcessedEvent event) {
    if (event.getIngestionRunId() == null || event.getSourceRecordId() == null) return null;
    return changes
        .findFirstByIngestionRunIdAndShortageRecordSourceRecordIdOrderByOccurredAtDesc(
            event.getIngestionRunId(), event.getSourceRecordId())
        .orElse(null);
  }

  public Page<ProcessedEvent> events(String state, int page, int size) {
    PageRequest request =
        PageRequest.of(
            page,
            Math.min(size, 50),
            Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.asc("eventId")));
    return state == null || state.isBlank()
        ? events.findAll(request)
        : events.findByProcessingState(state, request);
  }

  public record EventFlow(ProcessedEvent event, List<EventStep> steps) {
    public EventFlow {
      steps = List.copyOf(steps);
    }
  }

  public record EventStep(
      String code, String label, String state, Instant occurredAt, String detail) {}
}
