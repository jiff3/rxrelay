package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.Medication;
import dev.rxrelay.core.domain.ShortageStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {
  Optional<Medication> findByRxCui(String rxCui);

  Optional<Medication> findByNormalizedName(String normalizedName);

  @Query(
      value =
          "select distinct m from Medication m "
              + "left join ShortageRecord s on s.medication = m "
              + "left join s.drugProduct p left join p.manufacturer mf "
              + "where (:query = '' or lower(m.canonicalName) like lower(concat('%', :query, '%')) "
              + "or lower(coalesce(m.genericName, '')) like lower(concat('%', :query, '%'))) "
              + "and (:status is null or s.status = :status) "
              + "and (:manufacturer = '' or lower(coalesce(mf.sourceName, '')) like lower(concat('%', :manufacturer, '%')))",
      countQuery =
          "select count(distinct m.id) from Medication m "
              + "left join ShortageRecord s on s.medication = m "
              + "left join s.drugProduct p left join p.manufacturer mf "
              + "where (:query = '' or lower(m.canonicalName) like lower(concat('%', :query, '%')) "
              + "or lower(coalesce(m.genericName, '')) like lower(concat('%', :query, '%'))) "
              + "and (:status is null or s.status = :status) "
              + "and (:manufacturer = '' or lower(coalesce(mf.sourceName, '')) like lower(concat('%', :manufacturer, '%')))")
  Page<Medication> searchFiltered(
      @Param("query") String query,
      @Param("status") ShortageStatus status,
      @Param("manufacturer") String manufacturer,
      Pageable pageable);
}
