package dev.rxrelay.core.service;

import dev.rxrelay.core.domain.Medication;
import dev.rxrelay.core.domain.ShortageRecord;
import dev.rxrelay.core.domain.ShortageStatus;
import dev.rxrelay.core.domain.StatusChange;
import dev.rxrelay.core.repository.MedicationRepository;
import dev.rxrelay.core.repository.ShortageRecordRepository;
import dev.rxrelay.core.repository.StatusChangeRepository;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MedicationQueryService {
  private final MedicationRepository medications;
  private final ShortageRecordRepository shortages;
  private final StatusChangeRepository changes;

  public MedicationQueryService(
      MedicationRepository medications,
      ShortageRecordRepository shortages,
      StatusChangeRepository changes) {
    this.medications = medications;
    this.shortages = shortages;
    this.changes = changes;
  }

  @Cacheable(
      cacheNames = "medication-search",
      key =
          "#p0.toLowerCase() + ':' + #p1 + ':' + #p2.toLowerCase() + ':' + #p3 + ':' + #p4 + ':' + #p5")
  public Page<MedicationSnapshot> search(
      String query, ShortageStatus status, String manufacturer, int page, int size, String sort) {
    Page<Medication> result =
        medications.searchFiltered(
            query.strip(),
            status,
            manufacturer.strip(),
            PageRequest.of(page, Math.min(size, 50), parseSort(sort)));
    Map<UUID, List<String>> statuses = shortageStatuses(result.getContent());
    return result.map(
        value -> MedicationSnapshot.from(value, statuses.getOrDefault(value.getId(), List.of())));
  }

  @Cacheable(cacheNames = "medication-detail", key = "#id")
  public MedicationSnapshot get(UUID id) {
    Medication medication = requireMedication(id);
    return MedicationSnapshot.from(
        medication, shortageStatuses(List.of(medication)).getOrDefault(id, List.of()));
  }

  public Page<ShortageRecord> shortages(UUID id, int page, int size) {
    requireMedication(id);
    return shortages.findByMedicationId(
        id,
        PageRequest.of(
            page,
            Math.min(size, 50),
            Sort.by(Sort.Order.desc("sourceUpdatedAt"), Sort.Order.asc("id"))));
  }

  public Page<StatusChange> timeline(UUID id, int page, int size) {
    requireMedication(id);
    return changes.findByShortageRecordMedicationId(
        id,
        PageRequest.of(
            page,
            Math.min(size, 50),
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id"))));
  }

  private Medication requireMedication(UUID id) {
    return medications.findById(id).orElseThrow(() -> new NotFoundException("Drug not found"));
  }

  public Map<UUID, List<String>> shortageStatuses(Collection<Medication> values) {
    if (values.isEmpty()) return Map.of();
    Map<UUID, List<String>> result = new HashMap<>();
    shortages.findStatusesByMedicationIds(values.stream().map(Medication::getId).toList()).stream()
        .sorted(
            java.util.Comparator.comparing(
                    (ShortageRecordRepository.MedicationStatus value) -> value.getMedicationId())
                .thenComparing(value -> value.getStatus().name()))
        .forEach(
            value ->
                result
                    .computeIfAbsent(value.getMedicationId(), ignored -> new ArrayList<>())
                    .add(value.getStatus().name()));
    return result;
  }

  private Sort parseSort(String value) {
    String[] parts = value.toLowerCase(Locale.ROOT).split(",", 2);
    String property =
        switch (parts[0]) {
          case "name" -> "canonicalName";
          case "updatedat" -> "updatedAt";
          default -> throw new IllegalArgumentException("sort must be name or updatedAt");
        };
    Sort.Direction direction =
        parts.length == 2
            ? Sort.Direction.fromOptionalString(parts[1])
                .orElseThrow(
                    () -> new IllegalArgumentException("sort direction must be asc or desc"))
            : Sort.Direction.ASC;
    return Sort.by(new Sort.Order(direction, property), Sort.Order.asc("id"));
  }

  public record MedicationSnapshot(
      UUID id,
      String name,
      String genericName,
      String rxCui,
      String dosageForm,
      String sourceName,
      String normalizationStatus,
      List<String> shortageStatuses,
      Instant updatedAt)
      implements Serializable {
    public MedicationSnapshot {
      shortageStatuses = List.copyOf(shortageStatuses);
    }

    public static MedicationSnapshot from(Medication value) {
      return from(value, List.of());
    }

    public static MedicationSnapshot from(Medication value, List<String> shortageStatuses) {
      return new MedicationSnapshot(
          value.getId(),
          value.getCanonicalName(),
          value.getGenericName(),
          value.getRxCui(),
          value.getDosageForm(),
          value.getSourceName(),
          value.getNormalizationStatus(),
          List.copyOf(shortageStatuses),
          value.getUpdatedAt());
    }
  }
}
