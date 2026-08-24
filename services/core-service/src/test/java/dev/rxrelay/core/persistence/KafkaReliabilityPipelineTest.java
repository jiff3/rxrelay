package dev.rxrelay.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.rxrelay.core.CoreServiceApplication;
import dev.rxrelay.core.repository.MedicationRepository;
import dev.rxrelay.core.repository.NotificationRepository;
import dev.rxrelay.core.repository.OutboxEventRepository;
import dev.rxrelay.core.repository.ProcessedEventRepository;
import dev.rxrelay.core.repository.StatusChangeRepository;
import dev.rxrelay.core.service.ReliabilityFaultInjector;
import dev.rxrelay.core.service.WatchlistService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@EnabledIfSystemProperty(named = "rxrelay.reliability", matches = "true")
class KafkaReliabilityPipelineTest {
  private static final String INPUT = "rxrelay.shortage.observed.v1";
  private static final String AVAILABILITY = "rxrelay.availability.changed.v1";
  private static final String DLQ = "rxrelay.availability.dlq.v1";
  private static final String NOTIFICATIONS = "rxrelay.notification.created.v1";

  @Test
  void duplicateRetryAndMalformedEventsRecoverEndToEnd() throws Exception {
    EmbeddedKafkaKraftBroker kafka =
        new EmbeddedKafkaKraftBroker(1, 1, INPUT, AVAILABILITY, DLQ, NOTIFICATIONS);
    kafka.afterPropertiesSet();
    try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
      String databaseUrl = postgres.getPostgresDatabase().getConnection().getMetaData().getURL();
      try (ConfigurableApplicationContext context =
          new SpringApplicationBuilder(CoreServiceApplication.class)
              .profiles("reliability-lab")
              .run(
                  "--server.port=0",
                  "--spring.datasource.url=" + databaseUrl,
                  "--spring.datasource.username=postgres",
                  "--spring.datasource.password=",
                  "--spring.cache.type=simple",
                  "--spring.data.redis.repositories.enabled=false",
                  "--spring.kafka.bootstrap-servers=" + kafka.getBrokersAsString(),
                  "--spring.kafka.consumer.group-id=rxrelay-reliability-pipeline",
                  "--rxrelay.outbox.poll-delay-ms=100",
                  "--rxrelay.kafka.retry.initial-interval-ms=25",
                  "--rxrelay.kafka.retry.max-interval-ms=50",
                  "--rxrelay.kafka.retry.max-retries=2")) {
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        KafkaTemplate<String, String> template = kafkaTemplate(context);
        ProcessedEventRepository processed = context.getBean(ProcessedEventRepository.class);
        StatusChangeRepository changes = context.getBean(StatusChangeRepository.class);
        NotificationRepository notifications = context.getBean(NotificationRepository.class);
        MedicationRepository medications = context.getBean(MedicationRepository.class);
        OutboxEventRepository outbox = context.getBean(OutboxEventRepository.class);
        WatchlistService watchlists = context.getBean(WatchlistService.class);

        ObjectNode baseline = fixtureEvent(mapper);
        String baselineJson = mapper.writeValueAsString(baseline);
        for (int copy = 0; copy < 3; copy++) template.send(INPUT, "openfda", baselineJson).get();
        await(() -> processed.count() == 1 && changes.count() == 1, Duration.ofSeconds(20));

        var medication =
            medications.findByNormalizedName("reliability lab medication fixture").orElseThrow();
        var watchlist = watchlists.create("local-demo-user", "Reliability pipeline");
        watchlists.addItem("local-demo-user", watchlist.getId(), medication.getId());

        ObjectNode changed = baseline.deepCopy();
        changed.put("eventId", "44444444-4444-4444-8444-444444444444");
        changed.put("ingestionRunId", "55555555-5555-4555-8555-555555555555");
        changed.put("correlationId", "55555555-5555-4555-8555-555555555555");
        changed.put("occurredAt", "2026-08-24T12:00:00Z");
        ObjectNode changedPayload = (ObjectNode) changed.path("payload");
        changedPayload.put("stateFingerprint", "c".repeat(64));
        changedPayload.put("normalizedStatus", "RESOLVED");
        ObjectNode changedValues = (ObjectNode) changedPayload.path("sourceValues");
        changedValues.put("status", "Resolved");
        changedValues.put("updateDate", "2026-08-24T00:00:00Z");
        String changedJson = mapper.writeValueAsString(changed);
        for (int copy = 0; copy < 3; copy++) template.send(INPUT, "openfda", changedJson).get();
        await(
            () -> processed.count() == 2 && changes.count() == 2 && notifications.count() == 1,
            Duration.ofSeconds(20));

        ObjectNode retry = baseline.deepCopy();
        String retryId = "66666666-6666-4666-8666-666666666666";
        retry.put("eventId", retryId);
        retry.put("ingestionRunId", "77777777-7777-4777-8777-777777777777");
        retry.put("correlationId", "77777777-7777-4777-8777-777777777777");
        ((ObjectNode) retry.path("payload")).put("sourceRecordId", "reliability-retry-fixture");
        ((ObjectNode) retry.path("payload")).put("normalizedName", "retry fixture");
        ((ObjectNode) retry.path("payload")).put("canonicalName", "Retry Fixture");
        ((ObjectNode) retry.path("payload").path("sourceValues"))
            .put("genericName", "Retry Fixture");
        context.getBean(ReliabilityFaultInjector.class).arm(retryId, 2);
        template.send(INPUT, "openfda", mapper.writeValueAsString(retry)).get();
        await(
            () ->
                processed
                    .findById(retryId)
                    .map(
                        value ->
                            "PROCESSED".equals(value.getProcessingState())
                                && value.getRetryCount() == 2)
                    .orElse(false),
            Duration.ofSeconds(20));

        template.send(INPUT, "openfda", "{\"schemaVersion\":").get();
        await(
            () ->
                processed.findByProcessingState("DEAD_LETTERED", pageable()).getTotalElements()
                    == 1,
            Duration.ofSeconds(20));
        await(
            () ->
                outbox.findAll().stream().filter(value -> value.getPublishedAt() != null).count()
                    >= 4,
            Duration.ofSeconds(20));

        JsonNode deadLetter = mapper.readTree(singleRecord(kafka, DLQ));
        JsonNode notificationEvent = mapper.readTree(singleRecord(kafka, NOTIFICATIONS));
        assertThat(deadLetter.path("eventType").asText()).isEqualTo("IngestionEventDeadLettered");
        assertThat(deadLetter.path("payload").path("retryable").asBoolean()).isFalse();
        assertThat(notificationEvent.path("eventType").asText()).isEqualTo("NotificationCreated");
        assertThat(changes.count()).isEqualTo(3);
        assertThat(notifications.count()).isOne();
        assertThat(processed.count()).isEqualTo(4);
        System.out.println(
            "RXRELAY_RELIABILITY_COUNTS processed="
                + processed.count()
                + " changes="
                + changes.count()
                + " notifications="
                + notifications.count()
                + " retryCount="
                + processed.findById(retryId).orElseThrow().getRetryCount()
                + " deadLetters=1");
      }
    } finally {
      kafka.destroy();
    }
  }

  private static ObjectNode fixtureEvent(ObjectMapper mapper) throws Exception {
    Path path =
        Path.of(
                "..",
                "..",
                "scripts",
                "reliability",
                "fixtures",
                "synthetic-shortage-observed.v1.json")
            .toAbsolutePath()
            .normalize();
    assertThat(Files.isRegularFile(path)).isTrue();
    return (ObjectNode) mapper.readTree(path.toFile()).path("event");
  }

  private static org.springframework.data.domain.Pageable pageable() {
    return org.springframework.data.domain.PageRequest.of(0, 10);
  }

  @SuppressWarnings("unchecked")
  private static KafkaTemplate<String, String> kafkaTemplate(
      ConfigurableApplicationContext context) {
    return (KafkaTemplate<String, String>) context.getBean(KafkaTemplate.class);
  }

  private static String singleRecord(EmbeddedKafkaKraftBroker broker, String topic) {
    Map<String, Object> properties =
        KafkaTestUtils.consumerProps("inspect-" + UUID.randomUUID(), "false", broker);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    var consumer =
        new DefaultKafkaConsumerFactory<String, String>(
                properties, new StringDeserializer(), new StringDeserializer())
            .createConsumer();
    try {
      broker.consumeFromAnEmbeddedTopic(consumer, topic);
      return KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(10)).value();
    } finally {
      consumer.close();
    }
  }

  private static void await(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(50);
    }
    assertThat(condition.getAsBoolean()).as("condition within " + timeout).isTrue();
  }
}
