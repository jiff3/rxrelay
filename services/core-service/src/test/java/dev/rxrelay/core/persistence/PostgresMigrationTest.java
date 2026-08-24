package dev.rxrelay.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgresMigrationTest {
  @Test
  void migrationsCreateNormalizedProvenanceSchemaOnRealPostgres() throws Exception {
    try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
      Flyway.configure().dataSource(postgres.getPostgresDatabase()).load().migrate();
      Set<String> tables = new TreeSet<>();
      try (Connection connection = postgres.getPostgresDatabase().getConnection();
          Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "select tablename from pg_tables where schemaname = 'public'")) {
        while (result.next()) tables.add(result.getString(1));
      }
      assertThat(tables)
          .contains(
              "ingestion_runs",
              "manufacturers",
              "drug_products",
              "shortage_records",
              "shortage_observations",
              "status_changes",
              "processed_events",
              "outbox_events",
              "audit_events");
      assertThat(tables)
          .contains("watchlists", "watchlist_items")
          .doesNotContain("watchlist_entries");
      Set<String> processedColumns = new TreeSet<>();
      Set<String> indexes = new TreeSet<>();
      try (Connection connection = postgres.getPostgresDatabase().getConnection();
          Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "select column_name from information_schema.columns "
                      + "where table_schema='public' and table_name='processed_events'")) {
        while (result.next()) processedColumns.add(result.getString(1));
      }
      try (Connection connection = postgres.getPostgresDatabase().getConnection();
          Statement statement = connection.createStatement();
          ResultSet result =
              statement.executeQuery(
                  "select indexname from pg_indexes where schemaname='public'")) {
        while (result.next()) indexes.add(result.getString(1));
      }
      assertThat(processedColumns)
          .contains(
              "processing_state",
              "received_at",
              "retry_count",
              "dead_lettered_at",
              "dead_letter_topic",
              "source_topic");
      assertThat(indexes)
          .contains(
              "idx_processed_events_state_received",
              "uq_notifications_owner_source_event",
              "idx_outbox_failed");
    }
  }
}
