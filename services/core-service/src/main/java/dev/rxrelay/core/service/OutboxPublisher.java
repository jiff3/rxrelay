package dev.rxrelay.core.service;

import dev.rxrelay.core.domain.OutboxEvent;
import dev.rxrelay.core.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxEventRepository events;
  private final KafkaTemplate<String, String> kafka;
  private final Clock clock;
  private final int maximumAttempts;
  private final long sendTimeoutSeconds;
  private final Counter published;
  private final Counter failed;

  public OutboxPublisher(
      OutboxEventRepository events,
      KafkaTemplate<String, String> kafka,
      Clock clock,
      MeterRegistry meterRegistry,
      @Value("${rxrelay.outbox.max-attempts:8}") int maximumAttempts,
      @Value("${rxrelay.outbox.send-timeout-seconds:10}") long sendTimeoutSeconds) {
    this.events = events;
    this.kafka = kafka;
    this.clock = clock;
    this.maximumAttempts = maximumAttempts;
    this.sendTimeoutSeconds = sendTimeoutSeconds;
    this.published = meterRegistry.counter("rxrelay.outbox.events", "outcome", "published");
    this.failed = meterRegistry.counter("rxrelay.outbox.events", "outcome", "failed");
  }

  @Scheduled(fixedDelayString = "${rxrelay.outbox.poll-delay-ms:1000}")
  @Transactional
  public void publishPending() {
    for (OutboxEvent event : events.lockPending(clock.instant())) {
      try {
        kafka
            .send(event.getTopic(), event.getAggregateKey(), event.getPayload())
            .get(sendTimeoutSeconds, TimeUnit.SECONDS);
        event.published(clock.instant());
        published.increment();
        log.atInfo()
            .addKeyValue("eventId", event.getId())
            .addKeyValue("correlationId", event.getCorrelationId())
            .addKeyValue("eventType", event.getEventType())
            .addKeyValue("topic", event.getTopic())
            .log("Outbox event published");
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        fail(event, interrupted);
        return;
      } catch (Exception exception) {
        fail(event, exception);
      }
    }
  }

  private void fail(OutboxEvent event, Exception exception) {
    String message =
        exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    Instant now = clock.instant();
    long retrySeconds = Math.min(60, 1L << Math.min(event.getAttemptCount(), 6));
    event.failed(message, now, now.plus(Duration.ofSeconds(retrySeconds)), maximumAttempts);
    failed.increment();
    log.atWarn()
        .addKeyValue("eventId", event.getId())
        .addKeyValue("correlationId", event.getCorrelationId())
        .addKeyValue("eventType", event.getEventType())
        .addKeyValue("attempt", event.getAttemptCount())
        .addKeyValue("exhausted", event.getFailedAt() != null)
        .log("Outbox publication failed: {}", exception.getClass().getSimpleName());
  }
}
