package dev.rxrelay.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medications")
public class Medication {
  @Id private UUID id;

  @Column(name = "canonical_name", nullable = false)
  private String canonicalName;

  @Column(name = "normalized_name", nullable = false, unique = true)
  private String normalizedName;

  @Column(name = "rx_cui", unique = true)
  private String rxCui;

  @Column(name = "generic_name")
  private String genericName;

  @Column(name = "dosage_form")
  private String dosageForm;

  @Column(name = "source_name")
  private String sourceName;

  @Column(name = "normalization_status", nullable = false)
  private String normalizationStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Medication() {}

  public Medication(
      String canonicalName,
      String normalizedName,
      String rxCui,
      String genericName,
      String dosageForm,
      String sourceName,
      String normalizationStatus,
      Instant now) {
    this.id = UUID.randomUUID();
    this.canonicalName = canonicalName;
    this.normalizedName = normalizedName;
    this.rxCui = rxCui;
    this.genericName = genericName;
    this.dosageForm = dosageForm;
    this.sourceName = sourceName;
    this.normalizationStatus = normalizationStatus;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public String getNormalizedName() {
    return normalizedName;
  }

  public String getRxCui() {
    return rxCui;
  }

  public String getGenericName() {
    return genericName;
  }

  public String getDosageForm() {
    return dosageForm;
  }

  public String getSourceName() {
    return sourceName;
  }

  public String getNormalizationStatus() {
    return normalizationStatus;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void refresh(
      String canonicalName,
      String rxCui,
      String genericName,
      String dosageForm,
      String sourceName,
      String normalizationStatus,
      Instant now) {
    this.canonicalName = canonicalName;
    if (this.rxCui == null) this.rxCui = rxCui;
    this.genericName = genericName;
    this.dosageForm = dosageForm;
    this.sourceName = sourceName;
    this.normalizationStatus = normalizationStatus;
    this.updatedAt = now;
  }
}
