package dev.rxrelay.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rxrelay.core.domain.ShortageStatus;
import dev.rxrelay.core.repository.MedicationRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MedicationRepositoryPostgresTest {
  private static final EmbeddedPostgres POSTGRES = startPostgres();

  @Autowired JdbcTemplate jdbc;
  @Autowired MedicationRepository medications;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> url());
    properties.add("spring.datasource.username", () -> "postgres");
    properties.add("spring.datasource.password", () -> "");
  }

  @BeforeEach
  void seedFixture() {
    jdbc.execute("TRUNCATE TABLE medications CASCADE");
    UUID medicationId = UUID.randomUUID();
    UUID manufacturerId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO medications (id, canonical_name, normalized_name, generic_name, source_name, normalization_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        medicationId,
        "Dextrose Fixture",
        "dextrose fixture",
        "Dextrose",
        "Dextrose source fixture",
        "UNRESOLVED",
        Timestamp.from(Instant.EPOCH),
        Timestamp.from(Instant.EPOCH));
    jdbc.update(
        "INSERT INTO manufacturers (id, source_name, normalized_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
        manufacturerId,
        "Acme Fixture Labs",
        "acme fixture labs",
        Timestamp.from(Instant.EPOCH),
        Timestamp.from(Instant.EPOCH));
    jdbc.update(
        "INSERT INTO drug_products (id, medication_id, manufacturer_id, source, source_product_key, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        productId,
        medicationId,
        manufacturerId,
        "fixture",
        "fixture-product",
        Timestamp.from(Instant.EPOCH),
        Timestamp.from(Instant.EPOCH));
    jdbc.update(
        "INSERT INTO shortage_records (id, source_record_id, medication_id, drug_product_id, status, payload_hash, last_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        "fixture-record",
        medicationId,
        productId,
        "CURRENT",
        "a".repeat(64),
        Timestamp.from(Instant.EPOCH));
  }

  @Test
  void filtersByTermStatusAndManufacturerWithDeterministicPage() {
    var page =
        medications.searchFiltered(
            "dex",
            ShortageStatus.CURRENT,
            "acme",
            PageRequest.of(0, 10, Sort.by("canonicalName").ascending().and(Sort.by("id"))));
    assertThat(page.getTotalElements()).isOne();
    assertThat(page.getContent().getFirst().getCanonicalName()).isEqualTo("Dextrose Fixture");
  }

  @AfterAll
  static void closePostgres() throws IOException {
    POSTGRES.close();
  }

  private static EmbeddedPostgres startPostgres() {
    try {
      return EmbeddedPostgres.builder().start();
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static String url() {
    return POSTGRES.getJdbcUrl("postgres", "postgres");
  }
}
