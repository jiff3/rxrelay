package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      value =
          "select * from outbox_events where published_at is null and failed_at is null "
              + "and available_at <= :now order by created_at limit 50 for update skip locked",
      nativeQuery = true)
  List<OutboxEvent> lockPending(@Param("now") Instant now);
}
