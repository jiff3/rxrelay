package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.WatchlistItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {
  @EntityGraph(attributePaths = "medication")
  Page<WatchlistItem> findByWatchlistId(UUID watchlistId, Pageable pageable);

  Optional<WatchlistItem> findByIdAndWatchlistId(UUID id, UUID watchlistId);

  boolean existsByWatchlistIdAndMedicationId(UUID watchlistId, UUID medicationId);

  @EntityGraph(attributePaths = {"watchlist", "medication"})
  List<WatchlistItem> findByMedicationId(UUID medicationId);

  @Query(
      "select distinct i.watchlist.ownerId from WatchlistItem i where i.medication.id = :medicationId")
  List<String> findDistinctOwnerIdsByMedicationId(UUID medicationId);

  long countByWatchlistId(UUID watchlistId);
}
