package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.DrugProduct;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrugProductRepository extends JpaRepository<DrugProduct, UUID> {
  Optional<DrugProduct> findBySourceAndSourceProductKey(String source, String sourceProductKey);
}
