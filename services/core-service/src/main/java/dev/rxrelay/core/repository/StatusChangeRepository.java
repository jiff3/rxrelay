package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.StatusChange;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, UUID> {
  @EntityGraph(attributePaths = {"shortageRecord", "shortageRecord.medication"})
  Page<StatusChange> findByShortageRecordMedicationId(UUID medicationId, Pageable pageable);

  @EntityGraph(attributePaths = {"shortageRecord", "shortageRecord.medication"})
  Page<StatusChange> findAllBy(Pageable pageable);

  @EntityGraph(attributePaths = {"shortageRecord", "shortageRecord.medication"})
  Optional<StatusChange>
      findFirstByIngestionRunIdAndShortageRecordSourceRecordIdOrderByOccurredAtDesc(
          UUID ingestionRunId, String sourceRecordId);
}
