package dev.rxrelay.core.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {
  private final KafkaAdmin kafkaAdmin;

  public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
    this.kafkaAdmin = kafkaAdmin;
  }

  @Override
  public Health health() {
    try {
      String clusterId = kafkaAdmin.clusterId();
      return clusterId == null
          ? Health.down().withDetail("reason", "cluster id unavailable").build()
          : Health.up().withDetail("clusterId", clusterId).build();
    } catch (RuntimeException exception) {
      return Health.down().withDetail("reason", exception.getClass().getSimpleName()).build();
    }
  }
}
