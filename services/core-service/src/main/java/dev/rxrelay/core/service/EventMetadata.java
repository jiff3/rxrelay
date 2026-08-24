package dev.rxrelay.core.service;

import java.time.Instant;

public record EventMetadata(
    Instant receivedAt, String topic, int partition, long offset, int deliveryAttempt) {}
