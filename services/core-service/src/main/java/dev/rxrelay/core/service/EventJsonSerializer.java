package dev.rxrelay.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class EventJsonSerializer {
  private final ObjectMapper objectMapper;
  private final Validator validator;

  public EventJsonSerializer(ObjectMapper objectMapper, Validator validator) {
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  public String serialize(Object event) {
    var violations = validator.validate(event);
    if (!violations.isEmpty()) {
      throw new IllegalStateException(
          "Outbound event contract violation at " + violations.iterator().next().getPropertyPath());
    }
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize outbound event", exception);
    }
  }
}
