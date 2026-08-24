package dev.rxrelay.core.domain;

public enum ShortageStatus {
  CURRENT,
  RESOLVED,
  TO_BE_DISCONTINUED,
  UNKNOWN;

  public static ShortageStatus fromSource(String value) {
    if (value == null) return UNKNOWN;
    String normalized = value.trim().toLowerCase();
    if (normalized.contains("resolve")) return RESOLVED;
    if (normalized.contains("discontinu")) return TO_BE_DISCONTINUED;
    if (normalized.contains("current") || normalized.contains("shortage")) return CURRENT;
    return UNKNOWN;
  }
}
