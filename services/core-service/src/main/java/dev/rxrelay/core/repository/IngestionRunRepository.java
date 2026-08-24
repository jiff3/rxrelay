package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.IngestionRun;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, UUID> {
  Page<IngestionRun> findAllBy(Pageable pageable);
}
