package dev.rxrelay.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rxrelay.core.repository.ProcessedEventRepository;
import java.time.Clock;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventFailureRecorder {
  private final ProcessedEventRepository events;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final EventMetrics metrics;

  public EventFailureRecorder(
      ProcessedEventRepository events,
      ObjectMapper objectMapper,
      Clock clock,
      EventMetrics metrics) {
    this.events = events;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.metrics = metrics;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void retry(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
    EventFailureDetails value =
        EventFailureDetails.from(record, exception, objectMapper, clock.instant());
    events.recordRetry(
        value.eventId(),
        value.eventType(),
        value.schemaVersion(),
        value.producer(),
        value.correlationId(),
        value.receivedAt(),
        // Spring's RetryListener reports 1 for the first failed delivery, so this is already the
        // number of failures/retries scheduled before the next attempt.
        Math.max(1, deliveryAttempt),
        value.errorCode(),
        value.topic(),
        value.partition(),
        value.offset());
    metrics.retry();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deadLetter(EventFailureDetails value, int deliveryAttempts, String deadLetterTopic) {
    events.recordDeadLetter(
        value.eventId(),
        value.eventType(),
        value.schemaVersion(),
        value.producer(),
        value.correlationId(),
        value.receivedAt(),
        Math.max(0, deliveryAttempts - 1),
        clock.instant(),
        deadLetterTopic,
        value.errorCode(),
        value.topic(),
        value.partition(),
        value.offset());
    metrics.deadLetter();
  }
}
