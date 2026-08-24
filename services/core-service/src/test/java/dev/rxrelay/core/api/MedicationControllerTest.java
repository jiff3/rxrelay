package dev.rxrelay.core.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.rxrelay.core.service.MedicationQueryService;
import dev.rxrelay.core.service.MedicationQueryService.MedicationSnapshot;
import dev.rxrelay.core.service.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MedicationController.class)
class MedicationControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean MedicationQueryService service;

  @Test
  void returnsBoundedPageAndCorrelationId() throws Exception {
    MedicationSnapshot value =
        new MedicationSnapshot(
            UUID.randomUUID(),
            "Fixture",
            "Fixture",
            null,
            "Tablet",
            "Fixture",
            "UNRESOLVED",
            List.of("CURRENT"),
            Instant.EPOCH);
    when(service.search(eq("fix"), isNull(), eq(""), eq(0), eq(20), eq("name,asc")))
        .thenReturn(new PageImpl<>(List.of(value), PageRequest.of(0, 20), 1));
    mvc.perform(get("/api/v1/drugs").param("query", "fix").header("X-Request-Id", "request-7"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "request-7"))
        .andExpect(jsonPath("$.items[0].name").value("Fixture"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void missingDrugUsesStructuredErrorWithoutStackTrace() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.get(id)).thenThrow(new NotFoundException("Drug not found"));
    mvc.perform(get("/api/v1/drugs/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("not_found"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void invalidPageSizeIsRejected() throws Exception {
    mvc.perform(get("/api/v1/drugs").param("size", "51"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"));
  }

  @Test
  void unsafeCorrelationIdIsNotReflected() throws Exception {
    when(service.search(eq(""), isNull(), eq(""), eq(0), eq(20), eq("name,asc")))
        .thenReturn(Page.empty());
    mvc.perform(get("/api/v1/drugs").header("X-Request-Id", "bad value"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("bad value")));
  }
}
