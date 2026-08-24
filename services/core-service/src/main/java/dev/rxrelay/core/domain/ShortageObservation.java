package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shortage_observations")
public class ShortageObservation {
  @Id private UUID id;

  @Column(name = "observation_event_id", nullable = false, unique = true)
  private String eventId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "shortage_record_id")
  private ShortageRecord shortageRecord;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ingestion_run_id")
  private IngestionRun ingestionRun;

  @Column(name = "source_payload_hash", nullable = false, length = 64)
  private String sourcePayloadHash;

  @Column(name = "state_fingerprint", nullable = false, length = 64)
  private String stateFingerprint;

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  protected ShortageObservation() {}

  public ShortageObservation(
      String eventId,
      ShortageRecord shortage,
      IngestionRun run,
      String payloadHash,
      String stateFingerprint,
      Instant observedAt) {
    this.id = UUID.randomUUID();
    this.eventId = eventId;
    this.shortageRecord = shortage;
    this.ingestionRun = run;
    this.sourcePayloadHash = payloadHash;
    this.stateFingerprint = stateFingerprint;
    this.observedAt = observedAt;
  }
}
