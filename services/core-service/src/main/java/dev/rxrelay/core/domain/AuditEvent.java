package dev.rxrelay.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
  @Id private UUID id;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ingestion_run_id")
  private IngestionRun ingestionRun;

  @Column(name = "source_record_id")
  private String sourceRecordId;

  @Column(name = "aggregate_type")
  private String aggregateType;

  @Column(name = "aggregate_id")
  private String aggregateId;

  @Column(nullable = false)
  private String message;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditEvent() {}

  public AuditEvent(
      String eventType,
      IngestionRun ingestionRun,
      String sourceRecordId,
      String message,
      Instant occurredAt) {
    this.id = UUID.randomUUID();
    this.eventType = eventType;
    this.ingestionRun = ingestionRun;
    this.sourceRecordId = sourceRecordId;
    this.message = message;
    this.occurredAt = occurredAt;
  }

  public static AuditEvent applicationEvent(
      String eventType,
      String aggregateType,
      UUID aggregateId,
      String message,
      Instant occurredAt) {
    AuditEvent event = new AuditEvent(eventType, null, null, message, occurredAt);
    event.aggregateType = aggregateType;
    event.aggregateId = aggregateId.toString();
    return event;
  }
}
