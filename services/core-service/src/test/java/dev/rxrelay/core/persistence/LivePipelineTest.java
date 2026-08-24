package dev.rxrelay.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rxrelay.core.CoreServiceApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

@EnabledIfSystemProperty(named = "rxrelay.live", matches = "true")
class LivePipelineTest {
  @Test
  void realOpenFdaRecordsTraverseKafkaAndPersistInPostgres() throws Exception {
    EmbeddedKafkaKraftBroker kafka = new EmbeddedKafkaKraftBroker(1, 1);
    kafka.afterPropertiesSet();
    try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
      String databaseUrl = postgres.getPostgresDatabase().getConnection().getMetaData().getURL();
      try (ConfigurableApplicationContext context =
          new SpringApplicationBuilder(CoreServiceApplication.class)
              .run(
                  "--server.port=0",
                  "--spring.datasource.url=" + databaseUrl,
                  "--spring.datasource.username=postgres",
                  "--spring.datasource.password=",
                  "--spring.cache.type=simple",
                  "--spring.data.redis.repositories.enabled=false",
                  "--spring.kafka.bootstrap-servers=" + kafka.getBrokersAsString(),
                  "--spring.kafka.consumer.group-id=rxrelay-live-pipeline",
                  "--rxrelay.outbox.poll-delay-ms=250")) {
        Path ingestion = Path.of("..", "ingestion-service").toAbsolutePath().normalize();
        Path python = ingestion.resolve(".venv/Scripts/python.exe");
        assertThat(Files.isExecutable(python)).isTrue();
        ProcessBuilder builder =
            new ProcessBuilder(
                    python.toString(), "-m", "rxrelay_ingestion.cli", "--limit", "3", "--runs", "2")
                .directory(ingestion.toFile())
                .redirectErrorStream(true);
        builder.environment().put("KAFKA_BOOTSTRAP_SERVERS", kafka.getBrokersAsString());
        builder.environment().put("SHORTAGE_TOPIC", "rxrelay.shortage.observed.v1");
        builder.environment().put("RXNORM_ENABLED", "true");
        Process process = builder.start();
        boolean finished = process.waitFor(90, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();

        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        long shortageCount = 0;
        while (Instant.now().isBefore(deadline)) {
          shortageCount = count(postgres, "shortage_records");
          if (shortageCount == 3
              && count(postgres, "shortage_observations") == 6
              && count(postgres, "audit_events") == 5) break;
          Thread.sleep(250);
        }
        assertThat(shortageCount).as(output).isEqualTo(3);
        assertThat(count(postgres, "ingestion_runs")).isEqualTo(2);
        assertThat(count(postgres, "shortage_observations")).isEqualTo(6);
        assertThat(count(postgres, "status_changes")).isEqualTo(3);
        assertThat(count(postgres, "outbox_events")).isEqualTo(3);
        assertThat(count(postgres, "audit_events")).isEqualTo(5);
        exerciseApi(context, postgres);
        System.out.println(
            "RXRELAY_LIVE_COUNTS shortages="
                + shortageCount
                + " observations="
                + count(postgres, "shortage_observations")
                + " changes="
                + count(postgres, "status_changes")
                + " audits="
                + count(postgres, "audit_events")
                + " runs="
                + count(postgres, "ingestion_runs"));
        System.out.println("RXRELAY_LIVE_SAMPLE " + sample(postgres));
        System.out.println("RXRELAY_QUERY_PLAN " + queryPlan(postgres));
      }
    } finally {
      kafka.destroy();
    }
  }

  private void exerciseApi(ConfigurableApplicationContext context, EmbeddedPostgres postgres)
      throws Exception {
    int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    String root = "http://localhost:" + port;
    HttpClient client = HttpClient.newHttpClient();
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    var drugs = send(client, root + "/api/v1/drugs?size=2", "GET", null);
    assertThat(drugs.statusCode()).isEqualTo(200);
    var overview = send(client, root + "/api/v1/overview", "GET", null);
    assertThat(overview.statusCode()).isEqualTo(200);
    assertThat(mapper.readTree(overview.body()).path("trackedShortageRecords").asLong())
        .isEqualTo(3);
    String drugId = mapper.readTree(drugs.body()).path("items").get(0).path("id").asText();
    assertThat(send(client, root + "/api/v1/drugs/" + drugId, "GET", null).statusCode())
        .isEqualTo(200);
    assertThat(
            send(client, root + "/api/v1/drugs/" + drugId + "/shortages", "GET", null).statusCode())
        .isEqualTo(200);
    assertThat(
            send(client, root + "/api/v1/drugs/" + drugId + "/timeline", "GET", null).statusCode())
        .isEqualTo(200);

    var created =
        send(client, root + "/api/v1/watchlists", "POST", "{\"name\":\"Live FDA watchlist\"}");
    assertThat(created.statusCode()).isEqualTo(201);
    String watchlistId = mapper.readTree(created.body()).path("id").asText();
    var added =
        send(
            client,
            root + "/api/v1/watchlists/" + watchlistId + "/items",
            "POST",
            "{\"drugId\":\"" + drugId + "\"}");
    assertThat(added.statusCode()).isEqualTo(201);
    String itemId = mapper.readTree(added.body()).path("id").asText();
    assertThat(send(client, root + "/api/v1/watchlists", "GET", null).statusCode()).isEqualTo(200);
    assertThat(send(client, root + "/api/v1/watchlists/" + watchlistId, "GET", null).statusCode())
        .isEqualTo(200);
    assertThat(
            send(
                    client,
                    root + "/api/v1/watchlists/" + watchlistId + "/items/" + itemId,
                    "DELETE",
                    null)
                .statusCode())
        .isEqualTo(204);
    assertThat(send(client, root + "/api/v1/notifications", "GET", null).statusCode())
        .isEqualTo(200);
    assertThat(send(client, root + "/api/v1/system/ingestion-runs", "GET", null).statusCode())
        .isEqualTo(200);
    String eventId =
        scalar(postgres, "select event_id from processed_events order by processed_at limit 1");
    assertThat(send(client, root + "/api/v1/system/events/" + eventId, "GET", null).statusCode())
        .isEqualTo(200);
    assertThat(
            send(client, root + "/api/v1/system/events/" + eventId + "/flow", "GET", null)
                .statusCode())
        .isEqualTo(200);
    assertThat(
            send(client, root + "/api/v1/watchlists/" + watchlistId, "DELETE", null).statusCode())
        .isEqualTo(204);
    assertThat(send(client, root + "/v3/api-docs", "GET", null).statusCode()).isEqualTo(200);
    assertThat(count(postgres, "audit_events")).isEqualTo(9);
    System.out.println(
        "RXRELAY_API_CHECKS overview,drugs,detail,shortages,timeline,watchlists,notifications,event-flow,openapi=passed");
  }

  private HttpResponse<String> send(HttpClient client, String url, String method, String body)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url)).header("X-Request-Id", "live-pipeline");
    if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
    else
      request
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(body));
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private long count(EmbeddedPostgres postgres, String table) throws Exception {
    try (Connection connection = postgres.getPostgresDatabase().getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("select count(*) from " + table)) {
      result.next();
      return result.getLong(1);
    }
  }

  private String sample(EmbeddedPostgres postgres) throws Exception {
    try (Connection connection = postgres.getPostgresDatabase().getConnection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "select s.source_record_id, m.source_name, s.source_status, s.normalized_status "
                    + "from shortage_records s join medications m on m.id = s.medication_id "
                    + "order by s.source_record_id limit 1")) {
      result.next();
      return result.getString(1)
          + " | "
          + result.getString(2)
          + " | "
          + result.getString(3)
          + " | "
          + result.getString(4);
    }
  }

  private String scalar(EmbeddedPostgres postgres, String sql) throws Exception {
    try (Connection connection = postgres.getPostgresDatabase().getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
    }
  }

  private String queryPlan(EmbeddedPostgres postgres) throws Exception {
    String sql =
        "explain (analyze, buffers, format text) select distinct m.id, m.canonical_name "
            + "from medications m left join shortage_records s on s.medication_id=m.id "
            + "left join drug_products p on p.id=s.drug_product_id "
            + "left join manufacturers mf on mf.id=p.manufacturer_id "
            + "where lower(m.canonical_name) like '%a%' and s.status='CURRENT' "
            + "order by m.canonical_name, m.id limit 20";
    try (Connection connection = postgres.getPostgresDatabase().getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      List<String> lines = new ArrayList<>();
      while (result.next()) {
        lines.add(result.getString(1));
      }
      return System.lineSeparator() + String.join(System.lineSeparator(), lines);
    }
  }
}
