package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watchlists")
public class Watchlist {
  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private String ownerId;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Watchlist() {}

  public Watchlist(String ownerId, String name, Instant now) {
    this.id = UUID.randomUUID();
    this.ownerId = ownerId;
    this.name = name;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
