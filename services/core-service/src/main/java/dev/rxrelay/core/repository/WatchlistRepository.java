package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.Watchlist;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {
  Page<Watchlist> findByOwnerId(String ownerId, Pageable pageable);

  Optional<Watchlist> findByIdAndOwnerId(UUID id, String ownerId);

  boolean existsByOwnerIdAndNameIgnoreCase(String ownerId, String name);
}
