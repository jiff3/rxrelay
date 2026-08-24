package dev.rxrelay.core.config;

import dev.rxrelay.core.service.DeadLetterRecoverer;
import dev.rxrelay.core.service.EventFailureRecorder;
import dev.rxrelay.core.service.NonRetryableEventException;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfiguration {
  @Bean
  ConsumerFactory<String, String> shortageConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
      @Value("${spring.kafka.consumer.group-id}") String groupId) {
    return new DefaultKafkaConsumerFactory<>(
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG,
            groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            false),
        new StringDeserializer(),
        new StringDeserializer());
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> shortageConsumerFactory,
      DeadLetterRecoverer recoverer,
      EventFailureRecorder failureRecorder,
      @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup,
      @Value("${rxrelay.kafka.retry.initial-interval-ms:500}") long initialInterval,
      @Value("${rxrelay.kafka.retry.max-interval-ms:5000}") long maxInterval,
      @Value("${rxrelay.kafka.retry.max-retries:2}") int maxRetries) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(shortageConsumerFactory);
    factory.setAutoStartup(autoStartup);
    factory.getContainerProperties().setDeliveryAttemptHeader(true);
    ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, 2.0);
    backOff.setMaxInterval(maxInterval);
    backOff.setMaxAttempts(maxRetries);
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
    handler.addNotRetryableExceptions(NonRetryableEventException.class);
    handler.setRetryListeners(failureRecorder::retry);
    factory.setCommonErrorHandler(handler);
    return factory;
  }
}
