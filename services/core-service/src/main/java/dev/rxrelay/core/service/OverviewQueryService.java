package dev.rxrelay.core.service;

import dev.rxrelay.core.domain.IngestionRun;
import dev.rxrelay.core.domain.StatusChange;
import dev.rxrelay.core.repository.IngestionRunRepository;
import dev.rxrelay.core.repository.MedicationRepository;
import dev.rxrelay.core.repository.NotificationRepository;
import dev.rxrelay.core.repository.ShortageRecordRepository;
import dev.rxrelay.core.repository.StatusChangeRepository;
import dev.rxrelay.core.service.MedicationQueryService.MedicationSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OverviewQueryService {
  private static final int RECENT_LIMIT = 6;

  private final MedicationRepository medications;
  private final ShortageRecordRepository shortages;
  private final StatusChangeRepository changes;
  private final IngestionRunRepository runs;
  private final NotificationRepository notifications;
  private final MedicationQueryService medicationQueries;

  public OverviewQueryService(
      MedicationRepository medications,
      ShortageRecordRepository shortages,
      StatusChangeRepository changes,
      IngestionRunRepository runs,
      NotificationRepository notifications,
      MedicationQueryService medicationQueries) {
    this.medications = medications;
    this.shortages = shortages;
    this.changes = changes;
    this.runs = runs;
    this.notifications = notifications;
    this.medicationQueries = medicationQueries;
  }

  public OverviewSnapshot overview(String ownerId) {
    List<StatusChange> recentChanges =
        changes
            .findAllBy(
                PageRequest.of(
                    0, RECENT_LIMIT, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id"))))
            .getContent();
    List<MedicationSnapshot> recentlyUpdated =
        medicationQueries.search("", null, "", 0, RECENT_LIMIT, "updatedAt,desc").getContent();
    Map<UUID, List<String>> recentStatuses =
        medicationQueries.shortageStatuses(
            recentChanges.stream()
                .map(value -> value.getShortageRecord().getMedication())
                .toList());
    List<RecentChangeSnapshot> recentChangeSnapshots =
        recentChanges.stream()
            .map(
                value ->
                    new RecentChangeSnapshot(
                        value,
                        MedicationSnapshot.from(
                            value.getShortageRecord().getMedication(),
                            recentStatuses.getOrDefault(
                                value.getShortageRecord().getMedication().getId(), List.of()))))
            .toList();
    IngestionRun latestRun =
        runs
            .findAllBy(
                PageRequest.of(0, 1, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.asc("id"))))
            .stream()
            .findFirst()
            .orElse(null);
    return new OverviewSnapshot(
        medications.count(),
        shortages.count(),
        notifications.countByOwnerIdAndReadFalse(ownerId),
        recentChangeSnapshots,
        recentlyUpdated,
        latestRun);
  }

  public record OverviewSnapshot(
      long trackedMedications,
      long trackedShortageRecords,
      long unreadNotifications,
      List<RecentChangeSnapshot> recentChanges,
      List<MedicationSnapshot> recentlyUpdatedMedications,
      IngestionRun latestIngestionRun) {
    public OverviewSnapshot {
      recentChanges = List.copyOf(recentChanges);
      recentlyUpdatedMedications = List.copyOf(recentlyUpdatedMedications);
    }
  }

  public record RecentChangeSnapshot(StatusChange change, MedicationSnapshot medication) {}
}
