package dev.rxrelay.core.service;

public enum EventProcessingResult {
  PROCESSED,
  DUPLICATE,
  STALE_OBSERVATION
}
