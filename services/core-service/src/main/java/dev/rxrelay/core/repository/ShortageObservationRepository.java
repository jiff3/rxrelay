package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.ShortageObservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortageObservationRepository extends JpaRepository<ShortageObservation, UUID> {}
