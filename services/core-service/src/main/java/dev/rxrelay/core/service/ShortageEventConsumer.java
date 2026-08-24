package dev.rxrelay.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rxrelay.core.domain.IngestionEvent;
import java.nio.ByteBuffer;
import java.time.Clock;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Component
public class ShortageEventConsumer {
  private static final Logger log = LoggerFactory.getLogger(ShortageEventConsumer.class);

  private final ObjectMapper objectMapper;
  private final EventContractValidator validator;
  private final ShortageEventHandler handler;
  private final ObjectProvider<ReliabilityFaultInjector> faultInjector;
  private final EventMetrics metrics;
  private final Clock clock;

  public ShortageEventConsumer(
      ObjectMapper objectMapper,
      EventContractValidator validator,
      ShortageEventHandler handler,
      ObjectProvider<ReliabilityFaultInjector> faultInjector,
      EventMetrics metrics,
      Clock clock) {
    this.objectMapper = objectMapper;
    this.validator = validator;
    this.handler = handler;
    this.faultInjector = faultInjector;
    this.metrics = metrics;
    this.clock = clock;
  }

  @KafkaListener(
      topics = "${rxrelay.kafka.shortage-topic}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void receive(ConsumerRecord<String, String> record) {
    IngestionEvent event;
    try {
      event =
          objectMapper
              .readerFor(IngestionEvent.class)
              .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .readValue(record.value());
    } catch (JsonProcessingException exception) {
      throw new NonRetryableEventException("Malformed ingestion event JSON", exception);
    }
    validator.validate(event);
    EventMetadata metadata =
        new EventMetadata(
            clock.instant(),
            record.topic(),
            record.partition(),
            record.offset(),
            deliveryAttempt(record));
    try (MDC.MDCCloseable ignoredEvent = MDC.putCloseable("eventId", event.eventId());
        MDC.MDCCloseable ignoredCorrelation =
            MDC.putCloseable("correlationId", event.correlationId())) {
      faultInjector.ifAvailable(value -> value.maybeFail(event.eventId()));
      EventProcessingResult result = handler.handle(event, metadata);
      if (result == EventProcessingResult.DUPLICATE) metrics.duplicate();
      else {
        metrics.processed();
        if (result == EventProcessingResult.STALE_OBSERVATION) metrics.stale();
      }
      log.atInfo()
          .addKeyValue("eventId", event.eventId())
          .addKeyValue("correlationId", event.correlationId())
          .addKeyValue("eventType", event.eventType())
          .addKeyValue("processingResult", result)
          .addKeyValue("deliveryAttempt", metadata.deliveryAttempt())
          .log("Kafka event handling completed");
    }
  }

  private static int deliveryAttempt(ConsumerRecord<?, ?> record) {
    var header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
    if (header == null || header.value() == null || header.value().length != Integer.BYTES)
      return 1;
    return Math.max(1, ByteBuffer.wrap(header.value()).getInt());
  }
}
