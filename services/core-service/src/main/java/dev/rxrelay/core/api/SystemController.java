package dev.rxrelay.core.api;

import dev.rxrelay.core.config.DemoUserProvider;
import dev.rxrelay.core.service.SystemQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
  private final SystemQueryService service;
  private final DemoUserProvider users;

  public SystemController(SystemQueryService service, DemoUserProvider users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping("/ingestion-runs")
  ApiModels.PageResponse<ApiModels.IngestionRunView> runs(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiModels.PageResponse.from(
        service.runs(page, size).map(ApiModels.IngestionRunView::from));
  }

  @GetMapping("/events/{eventId}")
  ApiModels.ProcessedEventView event(@PathVariable @Size(max = 200) String eventId) {
    return ApiModels.ProcessedEventView.from(service.event(eventId));
  }

  @GetMapping("/events/{eventId}/flow")
  ApiModels.EventFlowView eventFlow(@PathVariable @Size(max = 200) String eventId) {
    return ApiModels.EventFlowView.from(service.eventFlow(eventId, users.currentUserId()));
  }

  @GetMapping("/events")
  ApiModels.PageResponse<ApiModels.ProcessedEventView> events(
      @RequestParam(required = false)
          @Size(max = 24)
          @Pattern(regexp = "(?i)PROCESSING|RETRYING|PROCESSED|DEAD_LETTERED")
          String state,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiModels.PageResponse.from(
        service
            .events(state == null ? null : state.toUpperCase(Locale.ROOT), page, size)
            .map(ApiModels.ProcessedEventView::from));
  }
}
