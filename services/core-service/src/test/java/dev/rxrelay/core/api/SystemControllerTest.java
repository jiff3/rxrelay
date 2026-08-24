package dev.rxrelay.core.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rxrelay.core.config.DemoUserProvider;
import dev.rxrelay.core.domain.ProcessedEvent;
import dev.rxrelay.core.service.SystemQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemController.class)
class SystemControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean SystemQueryService service;
  @MockitoBean DemoUserProvider users;

  @Test
  void listsSanitizedDeadLetterMetadataWithCaseInsensitiveState() throws Exception {
    ProcessedEvent event = mock(ProcessedEvent.class);
    when(event.getEventId()).thenReturn("11111111-1111-4111-8111-111111111111");
    when(event.getEventType()).thenReturn("Unknown");
    when(event.getProcessingState()).thenReturn("DEAD_LETTERED");
    when(event.getReceivedAt()).thenReturn(Instant.parse("2026-08-23T18:00:00Z"));
    when(event.getRetryCount()).thenReturn(0);
    when(event.getDeadLetterTopic()).thenReturn("rxrelay.availability.dlq.v1");
    when(event.getLastErrorCode()).thenReturn("NonRetryableEventException");
    when(service.events(eq("DEAD_LETTERED"), eq(0), eq(20)))
        .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1));

    mvc.perform(get("/api/v1/system/events").param("state", "dead_lettered"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].processingState").value("DEAD_LETTERED"))
        .andExpect(jsonPath("$.items[0].lastErrorCode").value("NonRetryableEventException"))
        .andExpect(jsonPath("$.items[0].deadLetterTopic").value("rxrelay.availability.dlq.v1"))
        .andExpect(jsonPath("$.items[0].payload").doesNotExist());
  }

  @Test
  void rejectsUnknownProcessingState() throws Exception {
    mvc.perform(get("/api/v1/system/events").param("state", "lost"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"));
  }
}
