package dev.rxrelay.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rxrelay.core.domain.OutboundEvents;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterRecoverer implements ConsumerRecordRecoverer {
  private static final Logger log = LoggerFactory.getLogger(DeadLetterRecoverer.class);

  private final KafkaTemplate<String, String> kafka;
  private final EventFailureRecorder recorder;
  private final EventJsonSerializer serializer;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final String deadLetterTopic;

  public DeadLetterRecoverer(
      KafkaTemplate<String, String> kafka,
      EventFailureRecorder recorder,
      EventJsonSerializer serializer,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${rxrelay.kafka.dead-letter-topic}") String deadLetterTopic) {
    this.kafka = kafka;
    this.recorder = recorder;
    this.serializer = serializer;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.deadLetterTopic = deadLetterTopic;
  }

  @Override
  public void accept(ConsumerRecord<?, ?> record, Exception exception) {
    EventFailureDetails details =
        EventFailureDetails.from(record, exception, objectMapper, clock.instant());
    int attempts = deliveryAttempts(record);
    UUID locationId =
        UUID.nameUUIDFromBytes(
            ("dead-letter:" + record.topic() + ":" + record.partition() + ":" + record.offset())
                .getBytes(StandardCharsets.UTF_8));
    OutboundEvents.DeadLettered event =
        new OutboundEvents.DeadLettered(
            "1.0",
            locationId,
            "IngestionEventDeadLettered",
            clock.instant(),
            uuidOr(details.correlationId(), locationId),
            "rxrelay-core",
            new OutboundEvents.DeadLetterPayload(
                record.topic(),
                record.partition(),
                record.offset(),
                details.eventId(),
                details.errorCode(),
                attempts,
                !hasCause(exception, NonRetryableEventException.class),
                details.rawValue()));
    try {
      kafka
          .send(deadLetterTopic, details.eventId(), serializer.serialize(event))
          .get(10, TimeUnit.SECONDS);
      recorder.deadLetter(details, attempts, deadLetterTopic);
      log.atWarn()
          .addKeyValue("eventId", details.eventId())
          .addKeyValue("correlationId", details.correlationId())
          .addKeyValue("deadLetterTopic", deadLetterTopic)
          .addKeyValue("deliveryAttempts", attempts)
          .log("Kafka event moved to dead letter topic");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Dead-letter publication interrupted", interrupted);
    } catch (Exception publishFailure) {
      throw new IllegalStateException("Dead-letter publication failed", publishFailure);
    }
  }

  private static int deliveryAttempts(ConsumerRecord<?, ?> record) {
    var header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
    if (header == null || header.value() == null || header.value().length != Integer.BYTES)
      return 1;
    return Math.max(1, ByteBuffer.wrap(header.value()).getInt());
  }

  private static UUID uuidOr(String value, UUID fallback) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return fallback;
    }
  }

  private static boolean hasCause(Throwable value, Class<? extends Throwable> type) {
    Throwable current = value;
    while (current != null) {
      if (type.isInstance(current)) return true;
      current = current.getCause();
    }
    return false;
  }
}
