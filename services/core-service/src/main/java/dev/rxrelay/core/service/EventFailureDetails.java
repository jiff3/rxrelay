package dev.rxrelay.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

record EventFailureDetails(
    String eventId,
    String eventType,
    String schemaVersion,
    String producer,
    String correlationId,
    Instant receivedAt,
    String errorCode,
    String topic,
    int partition,
    long offset,
    String rawValue) {

  static EventFailureDetails from(
      ConsumerRecord<?, ?> record, Exception exception, ObjectMapper mapper, Instant now) {
    String location = record.topic() + ":" + record.partition() + ":" + record.offset();
    UUID locationId = UUID.nameUUIDFromBytes(location.getBytes(StandardCharsets.UTF_8));
    String raw = record.value() == null ? "null" : record.value().toString();
    JsonNode node = null;
    try {
      node = mapper.readTree(raw);
    } catch (Exception ignored) {
      // A malformed value is expected on this path.
    }
    return new EventFailureDetails(
        text(node, "eventId", locationId.toString()),
        text(node, "eventType", "Unknown"),
        text(node, "schemaVersion", null),
        text(node, "producer", null),
        text(node, "correlationId", locationId.toString()),
        now,
        rootCause(exception).getClass().getSimpleName(),
        record.topic(),
        record.partition(),
        record.offset(),
        raw.substring(0, Math.min(raw.length(), 32768)));
  }

  private static String text(JsonNode node, String field, String fallback) {
    if (node == null || !node.path(field).isTextual() || node.path(field).asText().isBlank()) {
      return fallback;
    }
    return node.path(field).asText();
  }

  private static Throwable rootCause(Throwable value) {
    Throwable current = value;
    while (current.getCause() != null && current.getCause() != current)
      current = current.getCause();
    return current;
  }
}
