package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private String ownerId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "medication_id")
  private Medication medication;

  @Column(nullable = false)
  private String message;

  @Column(name = "is_read", nullable = false)
  private boolean read;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "source_event_id")
  private String sourceEventId;

  @Column(name = "correlation_id")
  private String correlationId;

  protected Notification() {}

  public Notification(String ownerId, Medication medication, String message, Instant createdAt) {
    this(UUID.randomUUID(), ownerId, medication, message, null, null, createdAt);
  }

  public Notification(
      UUID id,
      String ownerId,
      Medication medication,
      String message,
      String sourceEventId,
      String correlationId,
      Instant createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.medication = medication;
    this.message = message;
    this.sourceEventId = sourceEventId;
    this.correlationId = correlationId;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Medication getMedication() {
    return medication;
  }

  public String getMessage() {
    return message;
  }

  public boolean isRead() {
    return read;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void markRead() {
    this.read = true;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getSourceEventId() {
    return sourceEventId;
  }

  public String getCorrelationId() {
    return correlationId;
  }
}
