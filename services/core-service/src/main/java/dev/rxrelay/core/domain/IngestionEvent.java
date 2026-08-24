package dev.rxrelay.core.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record IngestionEvent(
    @NotBlank String schemaVersion,
    @NotBlank @Size(max = 200) String eventId,
    @NotBlank @Size(max = 64) String eventType,
    @NotNull Instant occurredAt,
    @NotBlank @Size(max = 128) String correlationId,
    @NotBlank @Size(max = 100) String producer,
    @NotBlank @Size(max = 50) String source,
    @NotBlank @Size(max = 200) String ingestionRunId,
    String runStatus,
    @Valid RunSummary summary,
    @Valid ObservationPayload payload) {

  public record RunSummary(
      @Positive int requested,
      @PositiveOrZero int fetched,
      @PositiveOrZero int published,
      @PositiveOrZero int malformed,
      @PositiveOrZero int normalizationUnresolved,
      @PositiveOrZero int normalizationAmbiguous,
      @PositiveOrZero int normalizationErrors,
      @NotNull @Size(max = 20) List<String> errors) {
    public RunSummary {
      errors = List.copyOf(errors);
    }
  }

  public record ObservationPayload(
      @NotBlank @Size(max = 500) String sourceRecordId,
      @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String sourcePayloadHash,
      @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String stateFingerprint,
      @NotBlank @Size(max = 300) String normalizedName,
      @NotBlank @Size(max = 300) String canonicalName,
      String rxCui,
      @NotBlank String normalizationStatus,
      String normalizationQuery,
      @NotBlank String normalizedStatus,
      @NotNull @Valid SourceValues sourceValues) {}

  public record SourceValues(
      @NotBlank @Size(max = 32) String packageNdc,
      @NotBlank @Size(max = 300) String genericName,
      @NotBlank @Size(max = 300) String companyName,
      @NotBlank String presentation,
      @NotBlank @Size(max = 100) String updateType,
      String availability,
      String relatedInfo,
      String relatedInfoLink,
      String resolvedNote,
      String shortageReason,
      @NotNull List<String> therapeuticCategories,
      String dosageForm,
      @NotBlank @Size(max = 100) String status,
      @NotNull Instant updateDate,
      Instant changeDate,
      Instant discontinuedDate,
      @NotNull Instant initialPostingDate) {
    public SourceValues {
      therapeuticCategories = List.copyOf(therapeuticCategories);
    }
  }
}
