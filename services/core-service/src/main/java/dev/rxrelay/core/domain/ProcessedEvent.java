package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
  @Id
  @Column(name = "event_id")
  private String eventId;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "event_type")
  private String eventType;

  @Column(name = "ingestion_run_id")
  private java.util.UUID ingestionRunId;

  @Column(name = "source_record_id")
  private String sourceRecordId;

  @Column(name = "schema_version")
  private String schemaVersion;

  private String producer;

  @Column(name = "correlation_id")
  private String correlationId;

  @Column(name = "processing_state", nullable = false)
  private String processingState;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "dead_lettered_at")
  private Instant deadLetteredAt;

  @Column(name = "dead_letter_topic")
  private String deadLetterTopic;

  @Column(name = "last_error_code")
  private String lastErrorCode;

  @Column(name = "source_topic")
  private String sourceTopic;

  @Column(name = "source_partition")
  private Integer sourcePartition;

  @Column(name = "source_offset")
  private Long sourceOffset;

  protected ProcessedEvent() {}

  public ProcessedEvent(
      String eventId,
      String eventType,
      java.util.UUID ingestionRunId,
      String sourceRecordId,
      Instant processedAt) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.ingestionRunId = ingestionRunId;
    this.sourceRecordId = sourceRecordId;
    this.processedAt = processedAt;
    this.receivedAt = processedAt;
    this.processingState = "PROCESSED";
  }

  public String getEventId() {
    return eventId;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public String getEventType() {
    return eventType;
  }

  public java.util.UUID getIngestionRunId() {
    return ingestionRunId;
  }

  public String getSourceRecordId() {
    return sourceRecordId;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public String getProducer() {
    return producer;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getProcessingState() {
    return processingState;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public Instant getDeadLetteredAt() {
    return deadLetteredAt;
  }

  public String getDeadLetterTopic() {
    return deadLetterTopic;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public String getSourceTopic() {
    return sourceTopic;
  }

  public Integer getSourcePartition() {
    return sourcePartition;
  }

  public Long getSourceOffset() {
    return sourceOffset;
  }
}
