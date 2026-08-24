package dev.rxrelay.core.service;

public class NonRetryableEventException extends RuntimeException {
  public NonRetryableEventException(String message) {
    super(message);
  }

  public NonRetryableEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
