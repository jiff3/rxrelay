package dev.rxrelay.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DemoUserProvider {
  private final String id;

  public DemoUserProvider(@Value("${rxrelay.demo-user.id}") String id) {
    if (id == null || id.isBlank() || id.length() > 100) {
      throw new IllegalArgumentException("Demo user ID must contain 1 to 100 characters");
    }
    this.id = id;
  }

  public String currentUserId() {
    return id;
  }
}
