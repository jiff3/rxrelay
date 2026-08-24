package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "shortage_records")
public class ShortageRecord {
  @Id private UUID id;

  @Column(name = "source_record_id", nullable = false, unique = true)
  private String sourceRecordId;

  @Column(nullable = false)
  private String source;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "medication_id")
  private Medication medication;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "drug_product_id")
  private DrugProduct drugProduct;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "latest_ingestion_run_id")
  private IngestionRun latestIngestionRun;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ShortageStatus status;

  private String availability;
  private String reason;
  private String company;
  private String presentation;

  @Column(name = "source_status")
  private String sourceStatus;

  @Column(name = "source_update_type")
  private String sourceUpdateType;

  @Column(name = "normalized_status")
  private String normalizedStatus;

  @Column(name = "state_fingerprint", length = 64)
  private String stateFingerprint;

  @Column(name = "resolved_note")
  private String resolvedNote;

  @Column(name = "related_info")
  private String relatedInfo;

  @Column(name = "related_info_link")
  private String relatedInfoLink;

  @Column(name = "source_updated_at")
  private Instant sourceUpdatedAt;

  @Column(name = "initial_posting_at")
  private Instant initialPostingAt;

  @Column(name = "source_change_at")
  private Instant sourceChangeAt;

  @Column(name = "discontinued_at")
  private Instant discontinuedAt;

  @Column(name = "payload_hash", nullable = false, length = 64)
  private String payloadHash;

  @Column(name = "first_seen_at")
  private Instant firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @ElementCollection
  @CollectionTable(
      name = "shortage_therapeutic_categories",
      joinColumns = @JoinColumn(name = "shortage_record_id"))
  @Column(name = "category", nullable = false)
  private Set<String> therapeuticCategories = new HashSet<>();

  protected ShortageRecord() {}

  public ShortageRecord(
      String sourceRecordId,
      Medication medication,
      DrugProduct product,
      IngestionRun run,
      IngestionEvent event,
      Instant now) {
    this.id = UUID.randomUUID();
    this.sourceRecordId = sourceRecordId;
    this.medication = medication;
    this.drugProduct = product;
    this.source = event.source();
    this.firstSeenAt = now;
    apply(run, event, now);
  }

  public ShortageStatus apply(IngestionRun run, IngestionEvent event, Instant now) {
    ShortageStatus previous = this.status;
    IngestionEvent.ObservationPayload payload = event.payload();
    IngestionEvent.SourceValues values = payload.sourceValues();
    this.latestIngestionRun = run;
    this.status = ShortageStatus.fromSource(payload.normalizedStatus());
    this.normalizedStatus = payload.normalizedStatus();
    this.sourceStatus = values.status();
    this.sourceUpdateType = values.updateType();
    this.availability = values.availability();
    this.reason = values.shortageReason();
    this.company = values.companyName();
    this.presentation = values.presentation();
    this.sourceUpdatedAt = values.updateDate();
    this.initialPostingAt = values.initialPostingDate();
    this.sourceChangeAt = values.changeDate();
    this.discontinuedAt = values.discontinuedDate();
    this.resolvedNote = values.resolvedNote();
    this.relatedInfo = values.relatedInfo();
    this.relatedInfoLink = values.relatedInfoLink();
    this.payloadHash = payload.sourcePayloadHash();
    this.stateFingerprint = payload.stateFingerprint();
    this.lastSeenAt = now;
    this.therapeuticCategories.clear();
    if (values.therapeuticCategories() != null) {
      this.therapeuticCategories.addAll(values.therapeuticCategories());
    }
    return previous;
  }

  public void reassociate(Medication medication, DrugProduct product) {
    this.medication = medication;
    this.drugProduct = product;
  }

  public boolean isOlderThanCurrent(Instant incomingSourceUpdatedAt) {
    return sourceUpdatedAt != null
        && incomingSourceUpdatedAt != null
        && incomingSourceUpdatedAt.isBefore(sourceUpdatedAt);
  }

  public UUID getId() {
    return id;
  }

  public String getSourceRecordId() {
    return sourceRecordId;
  }

  public String getSource() {
    return source;
  }

  public Medication getMedication() {
    return medication;
  }

  public ShortageStatus getStatus() {
    return status;
  }

  public String getAvailability() {
    return availability;
  }

  public String getReason() {
    return reason;
  }

  public String getCompany() {
    return company;
  }

  public String getPresentation() {
    return presentation;
  }

  public String getSourceStatus() {
    return sourceStatus;
  }

  public String getSourceUpdateType() {
    return sourceUpdateType;
  }

  public String getNormalizedStatus() {
    return normalizedStatus;
  }

  public Instant getSourceUpdatedAt() {
    return sourceUpdatedAt;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public String getStateFingerprint() {
    return stateFingerprint;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public DrugProduct getDrugProduct() {
    return drugProduct;
  }

  public String getResolvedNote() {
    return resolvedNote;
  }

  public String getRelatedInfo() {
    return relatedInfo;
  }

  public String getRelatedInfoLink() {
    return relatedInfoLink;
  }

  public Instant getInitialPostingAt() {
    return initialPostingAt;
  }

  public Instant getFirstSeenAt() {
    return firstSeenAt;
  }
}
