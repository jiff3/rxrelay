package dev.rxrelay.core.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OutboundEvents {
  private OutboundEvents() {}

  public record DrugAvailabilityChanged(
      @Pattern(regexp = "1\\.1") String schemaVersion,
      @NotNull UUID eventId,
      @Pattern(regexp = "DrugAvailabilityChanged") String eventType,
      @NotNull Instant occurredAt,
      @NotNull UUID correlationId,
      @Pattern(regexp = "rxrelay-core") String producer,
      @NotBlank String source,
      @NotBlank @Size(max = 500) String sourceRecordId,
      @NotNull UUID ingestionRunId,
      @NotNull UUID drugConceptId,
      @Pattern(regexp = "CURRENT|RESOLVED|TO_BE_DISCONTINUED|UNKNOWN") String previousStatus,
      @Pattern(regexp = "CURRENT|RESOLVED|TO_BE_DISCONTINUED|UNKNOWN") String newStatus,
      @NotNull Instant detectedAt,
      Instant sourceUpdatedAt) {}

  public record NotificationCreated(
      @Pattern(regexp = "1\\.0") String schemaVersion,
      @NotNull UUID eventId,
      @Pattern(regexp = "NotificationCreated") String eventType,
      @NotNull Instant occurredAt,
      @NotNull UUID correlationId,
      @Pattern(regexp = "rxrelay-core") String producer,
      @NotNull @Valid NotificationPayload payload) {}

  public record NotificationPayload(
      @NotNull UUID notificationId,
      @NotBlank @Size(max = 100) String ownerId,
      @NotNull UUID drugConceptId,
      @NotNull UUID availabilityEventId,
      @NotNull Instant createdAt) {}

  public record DeadLettered(
      @Pattern(regexp = "1\\.0") String schemaVersion,
      @NotNull UUID eventId,
      @Pattern(regexp = "IngestionEventDeadLettered") String eventType,
      @NotNull Instant occurredAt,
      @NotNull UUID correlationId,
      @Pattern(regexp = "rxrelay-core") String producer,
      @NotNull @Valid DeadLetterPayload payload) {}

  public record DeadLetterPayload(
      @NotBlank @Size(max = 200) String originalTopic,
      @PositiveOrZero int originalPartition,
      @PositiveOrZero long originalOffset,
      String originalEventId,
      @NotBlank @Size(max = 200) String failureCode,
      @Positive int deliveryAttempts,
      boolean retryable,
      @NotBlank @Size(max = 32768) String originalValue) {}
}
