package dev.rxrelay.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableKafka
@EnableScheduling
@EnableCaching
@SpringBootApplication
public class CoreServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(CoreServiceApplication.class, args);
  }
}
