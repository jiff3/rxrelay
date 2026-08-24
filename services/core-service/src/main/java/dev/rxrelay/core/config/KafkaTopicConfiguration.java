package dev.rxrelay.core.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {
  @Bean
  NewTopic shortageObservationTopic(@Value("${rxrelay.kafka.shortage-topic}") String topic) {
    return localTopic(topic);
  }

  @Bean
  NewTopic availabilityChangedTopic(@Value("${rxrelay.kafka.availability-topic}") String topic) {
    return localTopic(topic);
  }

  @Bean
  NewTopic notificationCreatedTopic(@Value("${rxrelay.kafka.notification-topic}") String topic) {
    return localTopic(topic);
  }

  @Bean
  NewTopic availabilityDeadLetterTopic(@Value("${rxrelay.kafka.dead-letter-topic}") String topic) {
    return localTopic(topic);
  }

  private static NewTopic localTopic(String name) {
    // One partition preserves source-record order and stays small for the local single-broker
    // KRaft stack. Larger deployments can replace these topic definitions externally.
    return TopicBuilder.name(name).partitions(1).replicas(1).build();
  }
}
