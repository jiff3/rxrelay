package dev.rxrelay.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "drug_products",
    uniqueConstraints = @UniqueConstraint(columnNames = {"source", "source_product_key"}))
public class DrugProduct {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "medication_id")
  private Medication medication;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manufacturer_id")
  private Manufacturer manufacturer;

  @Column(nullable = false)
  private String source;

  @Column(name = "source_product_key", nullable = false)
  private String sourceProductKey;

  @Column(name = "package_ndc")
  private String packageNdc;

  @Column(name = "dosage_form")
  private String dosageForm;

  private String presentation;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected DrugProduct() {}

  public DrugProduct(
      Medication medication,
      Manufacturer manufacturer,
      String source,
      String sourceProductKey,
      IngestionEvent.SourceValues values,
      Instant now) {
    this.id = UUID.randomUUID();
    this.medication = medication;
    this.manufacturer = manufacturer;
    this.source = source;
    this.sourceProductKey = sourceProductKey;
    apply(medication, manufacturer, values, now);
    this.createdAt = now;
  }

  public void apply(
      Medication medication,
      Manufacturer manufacturer,
      IngestionEvent.SourceValues values,
      Instant now) {
    this.medication = medication;
    this.manufacturer = manufacturer;
    this.packageNdc = values.packageNdc();
    this.dosageForm = values.dosageForm();
    this.presentation = values.presentation();
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public Manufacturer getManufacturer() {
    return manufacturer;
  }

  public String getPackageNdc() {
    return packageNdc;
  }

  public String getDosageForm() {
    return dosageForm;
  }
}
