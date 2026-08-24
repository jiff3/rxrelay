package dev.rxrelay.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rxrelay.core.CoreServiceApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/** Opt-in real-data server harness for manual frontend and screenshot verification. */
@EnabledIfSystemProperty(named = "rxrelay.frontend.live", matches = "true")
class FrontendLiveStackTest {
  private static final Path READY = Path.of("target", "frontend-live-stack.ready");
  private static final Path STOP = Path.of("target", "frontend-live-stack.stop");

  @Test
  void serveRealOpenFdaDataUntilStopMarker() throws Exception {
    Files.deleteIfExists(READY);
    Files.deleteIfExists(STOP);
    EmbeddedKafkaKraftBroker kafka = new EmbeddedKafkaKraftBroker(1, 1);
    kafka.afterPropertiesSet();
    try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
      String databaseUrl = postgres.getPostgresDatabase().getConnection().getMetaData().getURL();
      try (ConfigurableApplicationContext ignored =
          new SpringApplicationBuilder(CoreServiceApplication.class)
              .profiles("reliability-lab")
              .run(
                  "--server.port=8080",
                  "--spring.datasource.url=" + databaseUrl,
                  "--spring.datasource.username=postgres",
                  "--spring.datasource.password=",
                  "--spring.cache.type=simple",
                  "--spring.data.redis.repositories.enabled=false",
                  "--spring.kafka.bootstrap-servers=" + kafka.getBrokersAsString(),
                  "--spring.kafka.consumer.group-id=rxrelay-frontend-live",
                  "--rxrelay.outbox.poll-delay-ms=250")) {
        Process process = startIngestion(kafka);
        boolean finished = process.waitFor(90, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();

        Instant dataDeadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(dataDeadline) && count(postgres, "shortage_records") < 10) {
          Thread.sleep(250);
        }
        long shortages = count(postgres, "shortage_records");
        assertThat(shortages).as(output).isEqualTo(10);
        Files.writeString(
            READY,
            "{\"api\":\"http://127.0.0.1:8080\",\"shortageRecords\":" + shortages + "}",
            StandardCharsets.UTF_8);
        System.out.println(
            "RXRELAY_FRONTEND_LIVE_READY api=http://127.0.0.1:8080 shortages=" + shortages);

        Instant stopDeadline = Instant.now().plus(Duration.ofMinutes(10));
        while (!Files.exists(STOP) && Instant.now().isBefore(stopDeadline)) Thread.sleep(500);
        assertThat(Files.exists(STOP)).as("create %s after manual verification", STOP).isTrue();
      }
    } finally {
      Files.deleteIfExists(READY);
      Files.deleteIfExists(STOP);
      kafka.destroy();
    }
  }

  private Process startIngestion(EmbeddedKafkaKraftBroker kafka) throws Exception {
    Path ingestion = Path.of("..", "ingestion-service").toAbsolutePath().normalize();
    Path python = ingestion.resolve(".venv/Scripts/python.exe");
    assertThat(Files.isExecutable(python)).isTrue();
    ProcessBuilder builder =
        new ProcessBuilder(python.toString(), "-m", "rxrelay_ingestion.cli", "--limit", "10")
            .directory(ingestion.toFile())
            .redirectErrorStream(true);
    builder.environment().put("KAFKA_BOOTSTRAP_SERVERS", kafka.getBrokersAsString());
    builder.environment().put("SHORTAGE_TOPIC", "rxrelay.shortage.observed.v1");
    builder.environment().put("RXNORM_ENABLED", "true");
    return builder.start();
  }

  private long count(EmbeddedPostgres postgres, String table) throws Exception {
    try (Connection connection = postgres.getPostgresDatabase().getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("select count(*) from " + table)) {
      result.next();
      return result.getLong(1);
    }
  }
}
