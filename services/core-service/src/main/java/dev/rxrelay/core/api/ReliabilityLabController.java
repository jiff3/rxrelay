package dev.rxrelay.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rxrelay.core.service.ReliabilityFaultInjector;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Profile("reliability-lab")
@RequestMapping("/api/v1/reliability")
public class ReliabilityLabController {
  private final KafkaTemplate<String, String> kafka;
  private final ReliabilityFaultInjector faults;
  private final ObjectMapper objectMapper;
  private final String topic;

  public ReliabilityLabController(
      KafkaTemplate<String, String> kafka,
      ReliabilityFaultInjector faults,
      ObjectMapper objectMapper,
      @Value("${rxrelay.kafka.shortage-topic}") String topic) {
    this.kafka = kafka;
    this.faults = faults;
    this.objectMapper = objectMapper;
    this.topic = topic;
  }

  @PostMapping("/events")
  LabResult publish(
      @RequestBody String value, @RequestParam(defaultValue = "1") @Min(1) @Max(3) int copies)
      throws Exception {
    String eventId = objectMapper.readTree(value).path("eventId").asText();
    if (eventId.isBlank()) throw new IllegalArgumentException("Lab event requires eventId");
    for (int index = 0; index < copies; index++) {
      kafka.send(topic, "reliability-lab", value).get(10, TimeUnit.SECONDS);
    }
    return new LabResult("published", eventId, copies);
  }

  @PostMapping("/malformed")
  LabResult malformed() throws Exception {
    kafka.send(topic, "reliability-lab", "{\"schemaVersion\":").get(10, TimeUnit.SECONDS);
    return new LabResult("published-malformed", null, 1);
  }

  @PostMapping("/events/{eventId}/failures")
  LabResult armFailure(
      @PathVariable String eventId, @RequestParam(defaultValue = "1") @Min(1) @Max(3) int times) {
    faults.arm(eventId, times);
    return new LabResult("armed", eventId, times);
  }

  record LabResult(String action, String eventId, int count) {}
}
