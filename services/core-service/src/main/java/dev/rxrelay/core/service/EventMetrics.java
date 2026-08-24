package dev.rxrelay.core.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventMetrics {
  private final Counter processed;
  private final Counter duplicates;
  private final Counter retries;
  private final Counter deadLetters;
  private final Counter stale;

  public EventMetrics(MeterRegistry registry) {
    processed = registry.counter("rxrelay.kafka.events.processed");
    duplicates = registry.counter("rxrelay.kafka.events.duplicate");
    retries = registry.counter("rxrelay.kafka.events.retry");
    deadLetters = registry.counter("rxrelay.kafka.events.dead_letter");
    stale = registry.counter("rxrelay.kafka.events.stale");
  }

  public void processed() {
    processed.increment();
  }

  public void duplicate() {
    duplicates.increment();
  }

  public void retry() {
    retries.increment();
  }

  public void deadLetter() {
    deadLetters.increment();
  }

  public void stale() {
    stale.increment();
  }
}
