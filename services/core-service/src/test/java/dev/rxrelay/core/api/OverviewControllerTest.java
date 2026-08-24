package dev.rxrelay.core.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rxrelay.core.config.DemoUserProvider;
import dev.rxrelay.core.service.OverviewQueryService;
import dev.rxrelay.core.service.OverviewQueryService.OverviewSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OverviewController.class)
class OverviewControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean OverviewQueryService service;
  @MockitoBean DemoUserProvider users;

  @Test
  void returnsOnlyPersistedCountsAndCollections() throws Exception {
    when(users.currentUserId()).thenReturn("demo");
    when(service.overview("demo"))
        .thenReturn(new OverviewSnapshot(12, 18, 3, List.of(), List.of(), null));

    mvc.perform(get("/api/v1/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackedMedications").value(12))
        .andExpect(jsonPath("$.trackedShortageRecords").value(18))
        .andExpect(jsonPath("$.unreadNotifications").value(3))
        .andExpect(jsonPath("$.recentChanges").isArray())
        .andExpect(jsonPath("$.latestIngestionRun").doesNotExist());
  }
}
