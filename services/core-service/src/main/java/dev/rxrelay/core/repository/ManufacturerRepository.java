package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.Manufacturer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
  Optional<Manufacturer> findByNormalizedName(String normalizedName);
}
