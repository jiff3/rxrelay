package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  @EntityGraph(attributePaths = "medication")
  Page<Notification> findByOwnerId(String ownerId, Pageable pageable);

  @EntityGraph(attributePaths = "medication")
  Page<Notification> findByOwnerIdAndReadFalse(String ownerId, Pageable pageable);

  @EntityGraph(attributePaths = "medication")
  Optional<Notification> findByIdAndOwnerId(UUID id, String ownerId);

  @EntityGraph(attributePaths = "medication")
  Optional<Notification> findFirstByOwnerIdAndSourceEventIdOrderByCreatedAtAsc(
      String ownerId, String sourceEventId);

  long countByOwnerIdAndReadFalse(String ownerId);
}
