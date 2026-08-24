package dev.rxrelay.core.service;

import dev.rxrelay.core.domain.IngestionEvent;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EventContractValidator {
  private static final Set<String> EVENT_TYPES =
      Set.of("IngestionRunStarted", "ShortageObserved", "IngestionRunCompleted");
  private static final Set<String> RUN_STATUSES =
      Set.of("RUNNING", "SUCCEEDED", "PARTIAL", "FAILED");
  private static final Set<String> NORMALIZATION_STATUSES =
      Set.of("SOURCE_PROVIDED", "RESOLVED", "UNRESOLVED", "AMBIGUOUS", "ERROR", "SKIPPED");
  private static final Set<String> SHORTAGE_STATUSES =
      Set.of("CURRENT", "RESOLVED", "TO_BE_DISCONTINUED", "UNKNOWN");

  private final Validator validator;

  public EventContractValidator(Validator validator) {
    this.validator = validator;
  }

  public void validate(IngestionEvent event) {
    if (event == null) throw new NonRetryableEventException("Event body is null");
    var violations = validator.validate(event);
    if (!violations.isEmpty()) {
      String field = violations.iterator().next().getPropertyPath().toString();
      throw new NonRetryableEventException("Event contract violation at " + field);
    }
    if (!"1.1".equals(event.schemaVersion())) {
      throw new NonRetryableEventException("Unsupported ingestion schema version");
    }
    if (!"rxrelay-ingestion".equals(event.producer()) || !"openfda".equals(event.source())) {
      throw new NonRetryableEventException("Unsupported event producer or source");
    }
    if (!EVENT_TYPES.contains(event.eventType())) {
      throw new NonRetryableEventException("Unsupported ingestion event type");
    }
    requireUuid(event.eventId(), "eventId");
    requireUuid(event.correlationId(), "correlationId");
    requireUuid(event.ingestionRunId(), "ingestionRunId");

    if ("ShortageObserved".equals(event.eventType())) {
      if (event.payload() == null) {
        throw new NonRetryableEventException("ShortageObserved requires payload");
      }
      if (!NORMALIZATION_STATUSES.contains(event.payload().normalizationStatus())
          || !SHORTAGE_STATUSES.contains(event.payload().normalizedStatus())) {
        throw new NonRetryableEventException("Observation contains an unsupported status");
      }
    } else if (event.payload() != null) {
      throw new NonRetryableEventException(
          "Run lifecycle event cannot contain observation payload");
    }

    if ("IngestionRunCompleted".equals(event.eventType())) {
      if (event.summary() == null || !RUN_STATUSES.contains(event.runStatus())) {
        throw new NonRetryableEventException("Completed run requires summary and valid status");
      }
    }
  }

  private static void requireUuid(String value, String field) {
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new NonRetryableEventException(field + " must be a UUID", exception);
    }
  }
}
