package dev.rxrelay.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.rxrelay.core.domain.OutboxEvent;
import dev.rxrelay.core.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxPublisherTest {
  private static final Instant NOW = Instant.parse("2026-08-23T18:00:00Z");

  @Test
  void kafkaFailureIsBoundedAndEventuallyExhaustsEvent() {
    OutboxEventRepository events = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    OutboxEvent event = event();
    when(events.lockPending(NOW)).thenReturn(List.of(event));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
    OutboxPublisher publisher =
        new OutboxPublisher(
            events, kafka, Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry(), 2, 1);

    publisher.publishPending();
    publisher.publishPending();

    assertThat(event.getAttemptCount()).isEqualTo(2);
    assertThat(event.getFailedAt()).isEqualTo(NOW);
    assertThat(event.getPublishedAt()).isNull();
  }

  @Test
  void successfulSendMarksEventPublished() throws Exception {
    OutboxEventRepository events = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    OutboxEvent event = event();
    when(events.lockPending(NOW)).thenReturn(List.of(event));
    @SuppressWarnings("unchecked")
    SendResult<String, String> result = mock(SendResult.class);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(result));
    OutboxPublisher publisher =
        new OutboxPublisher(
            events, kafka, Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry(), 2, 1);

    publisher.publishPending();

    verify(kafka).send("topic", "key", "{}");
    assertThat(event.getPublishedAt()).isEqualTo(NOW);
    assertThat(event.getAttemptCount()).isZero();
  }

  private static OutboxEvent event() {
    return new OutboxEvent(
        UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
        "topic",
        "key",
        "EventType",
        "{}",
        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        NOW);
  }
}
