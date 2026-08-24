package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manufacturers")
public class Manufacturer {
  @Id private UUID id;

  @Column(name = "source_name", nullable = false)
  private String sourceName;

  @Column(name = "normalized_name", nullable = false, unique = true)
  private String normalizedName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Manufacturer() {}

  public Manufacturer(String sourceName, String normalizedName, Instant now) {
    this.id = UUID.randomUUID();
    this.sourceName = sourceName;
    this.normalizedName = normalizedName;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getSourceName() {
    return sourceName;
  }
}
