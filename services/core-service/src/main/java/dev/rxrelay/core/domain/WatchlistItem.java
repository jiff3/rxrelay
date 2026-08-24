package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watchlist_items")
public class WatchlistItem {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "watchlist_id")
  private Watchlist watchlist;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "medication_id")
  private Medication medication;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected WatchlistItem() {}

  public WatchlistItem(Watchlist watchlist, Medication medication, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.watchlist = watchlist;
    this.medication = medication;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Watchlist getWatchlist() {
    return watchlist;
  }

  public Medication getMedication() {
    return medication;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
