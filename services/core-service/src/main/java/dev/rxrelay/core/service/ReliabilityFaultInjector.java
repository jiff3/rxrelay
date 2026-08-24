package dev.rxrelay.core.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("reliability-lab")
public class ReliabilityFaultInjector {
  private final ConcurrentHashMap<String, AtomicInteger> remaining = new ConcurrentHashMap<>();

  public void arm(String eventId, int failures) {
    if (failures < 1 || failures > 3) throw new IllegalArgumentException("failures must be 1-3");
    remaining.put(eventId, new AtomicInteger(failures));
  }

  public void maybeFail(String eventId) {
    AtomicInteger counter = remaining.get(eventId);
    if (counter == null) return;
    int before = counter.getAndUpdate(current -> Math.max(0, current - 1));
    if (before > 0) {
      if (before == 1) remaining.remove(eventId, counter);
      throw new ReliabilityLabException("Reliability Lab injected consumer failure");
    }
  }

  public static class ReliabilityLabException extends RuntimeException {
    ReliabilityLabException(String message) {
      super(message);
    }
  }
}
