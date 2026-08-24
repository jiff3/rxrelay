package dev.rxrelay.core.api;

import dev.rxrelay.core.config.DemoUserProvider;
import dev.rxrelay.core.service.MedicationQueryService;
import dev.rxrelay.core.service.WatchlistService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final WatchlistService service;
  private final MedicationQueryService medications;
  private final DemoUserProvider users;

  public NotificationController(
      WatchlistService service, MedicationQueryService medications, DemoUserProvider users) {
    this.service = service;
    this.medications = medications;
    this.users = users;
  }

  @GetMapping
  ApiModels.PageResponse<ApiModels.NotificationView> list(
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    var values = service.notifications(users.currentUserId(), unreadOnly, page, size);
    var statuses =
        medications.shortageStatuses(
            values.getContent().stream().map(value -> value.getMedication()).toList());
    return ApiModels.PageResponse.from(
        values.map(
            value ->
                ApiModels.NotificationView.from(
                    value,
                    statuses.getOrDefault(value.getMedication().getId(), java.util.List.of()))));
  }

  @PatchMapping("/{id}/read")
  ApiModels.NotificationView markRead(@PathVariable UUID id) {
    var value = service.markRead(users.currentUserId(), id);
    var statuses = medications.shortageStatuses(java.util.List.of(value.getMedication()));
    return ApiModels.NotificationView.from(
        value, statuses.getOrDefault(value.getMedication().getId(), java.util.List.of()));
  }
}
