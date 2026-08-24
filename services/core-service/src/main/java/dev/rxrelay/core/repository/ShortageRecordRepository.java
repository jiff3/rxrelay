package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.ShortageRecord;
import dev.rxrelay.core.domain.ShortageStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortageRecordRepository extends JpaRepository<ShortageRecord, UUID> {
  Optional<ShortageRecord> findBySourceRecordId(String sourceRecordId);

  @EntityGraph(attributePaths = {"drugProduct", "drugProduct.manufacturer"})
  Page<ShortageRecord> findByMedicationId(UUID medicationId, Pageable pageable);

  @Query(
      "select s.medication.id as medicationId, s.status as status "
          + "from ShortageRecord s where s.medication.id in :medicationIds "
          + "group by s.medication.id, s.status")
  List<MedicationStatus> findStatusesByMedicationIds(
      @Param("medicationIds") Collection<UUID> medicationIds);

  interface MedicationStatus {
    UUID getMedicationId();

    ShortageStatus getStatus();
  }
}
