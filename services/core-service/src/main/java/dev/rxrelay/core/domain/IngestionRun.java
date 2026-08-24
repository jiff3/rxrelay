package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ingestion_runs")
public class IngestionRun {
  @Id private UUID id;

  @Column(nullable = false)
  private String source;

  @Column(nullable = false)
  private String status;

  @Column(name = "requested_count")
  private Integer requestedCount;

  @Column(name = "fetched_count")
  private Integer fetchedCount;

  @Column(name = "published_count")
  private Integer publishedCount;

  @Column(name = "malformed_count")
  private Integer malformedCount;

  @Column(name = "normalization_unresolved_count")
  private Integer normalizationUnresolvedCount;

  @Column(name = "normalization_ambiguous_count")
  private Integer normalizationAmbiguousCount;

  @Column(name = "normalization_error_count")
  private Integer normalizationErrorCount;

  @Column(name = "error_summary")
  private String errorSummary;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected IngestionRun() {}

  public IngestionRun(UUID id, String source, Instant startedAt) {
    this.id = id;
    this.source = source;
    this.status = "RUNNING";
    this.startedAt = startedAt;
  }

  public void complete(String status, IngestionEvent.RunSummary summary, Instant completedAt) {
    this.status = status;
    this.requestedCount = summary.requested();
    this.fetchedCount = summary.fetched();
    this.publishedCount = summary.published();
    this.malformedCount = summary.malformed();
    this.normalizationUnresolvedCount = summary.normalizationUnresolved();
    this.normalizationAmbiguousCount = summary.normalizationAmbiguous();
    this.normalizationErrorCount = summary.normalizationErrors();
    List<String> errors = summary.errors();
    this.errorSummary = errors == null || errors.isEmpty() ? null : String.join("\n", errors);
    this.completedAt = completedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getSource() {
    return source;
  }

  public String getStatus() {
    return status;
  }

  public Integer getRequestedCount() {
    return requestedCount;
  }

  public Integer getFetchedCount() {
    return fetchedCount;
  }

  public Integer getPublishedCount() {
    return publishedCount;
  }

  public Integer getMalformedCount() {
    return malformedCount;
  }

  public Integer getNormalizationUnresolvedCount() {
    return normalizationUnresolvedCount;
  }

  public Integer getNormalizationAmbiguousCount() {
    return normalizationAmbiguousCount;
  }

  public Integer getNormalizationErrorCount() {
    return normalizationErrorCount;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
