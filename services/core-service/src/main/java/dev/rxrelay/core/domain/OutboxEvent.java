package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  @Id private UUID id;

  @Column(nullable = false)
  private String topic;

  @Column(name = "aggregate_key", nullable = false)
  private String aggregateKey;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(nullable = false)
  private String payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "correlation_id")
  private String correlationId;

  @Column(name = "failed_at")
  private Instant failedAt;

  protected OutboxEvent() {}

  public OutboxEvent(
      UUID id,
      String topic,
      String key,
      String eventType,
      String payload,
      String correlationId,
      Instant now) {
    this.id = id;
    this.topic = topic;
    this.aggregateKey = key;
    this.eventType = eventType;
    this.payload = payload;
    this.correlationId = correlationId;
    this.createdAt = now;
    this.availableAt = now;
  }

  public void published(Instant now) {
    this.publishedAt = now;
    this.lastError = null;
  }

  public void failed(String error, Instant now, Instant retryAt, int maximumAttempts) {
    this.attemptCount++;
    this.lastError = error.substring(0, Math.min(error.length(), 1000));
    if (this.attemptCount >= maximumAttempts) this.failedAt = now;
    else this.availableAt = retryAt;
  }

  public UUID getId() {
    return id;
  }

  public String getTopic() {
    return topic;
  }

  public String getAggregateKey() {
    return aggregateKey;
  }

  public String getPayload() {
    return payload;
  }

  public String getEventType() {
    return eventType;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getFailedAt() {
    return failedAt;
  }
}
