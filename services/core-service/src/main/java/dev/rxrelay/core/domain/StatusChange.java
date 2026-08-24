package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "status_changes")
public class StatusChange {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "shortage_record_id")
  private ShortageRecord shortageRecord;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status")
  private ShortageStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_status", nullable = false)
  private ShortageStatus currentStatus;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "source_event_id", nullable = false, unique = true)
  private String sourceEventId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ingestion_run_id")
  private IngestionRun ingestionRun;

  @Column(name = "previous_state_fingerprint", length = 64)
  private String previousStateFingerprint;

  @Column(name = "new_state_fingerprint", length = 64)
  private String newStateFingerprint;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  protected StatusChange() {}

  public StatusChange(
      ShortageRecord record,
      ShortageStatus previous,
      ShortageStatus current,
      Instant occurredAt,
      String eventId,
      IngestionRun ingestionRun,
      String previousStateFingerprint,
      String newStateFingerprint) {
    this.id = UUID.randomUUID();
    this.shortageRecord = record;
    this.previousStatus = previous;
    this.currentStatus = current;
    this.occurredAt = occurredAt;
    this.sourceEventId = eventId;
    this.ingestionRun = ingestionRun;
    this.previousStateFingerprint = previousStateFingerprint;
    this.newStateFingerprint = newStateFingerprint;
    this.eventType = "DrugAvailabilityChanged";
  }

  public UUID getId() {
    return id;
  }

  public ShortageStatus getPreviousStatus() {
    return previousStatus;
  }

  public ShortageStatus getCurrentStatus() {
    return currentStatus;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getSourceEventId() {
    return sourceEventId;
  }

  public ShortageRecord getShortageRecord() {
    return shortageRecord;
  }
}
