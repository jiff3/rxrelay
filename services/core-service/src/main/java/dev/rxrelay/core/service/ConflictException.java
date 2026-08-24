package dev.rxrelay.core.service;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
