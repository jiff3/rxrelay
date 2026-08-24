package dev.rxrelay.core.api;

import dev.rxrelay.core.config.DemoUserProvider;
import dev.rxrelay.core.service.MedicationQueryService;
import dev.rxrelay.core.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {
  private final WatchlistService service;
  private final MedicationQueryService medications;
  private final DemoUserProvider users;

  public WatchlistController(
      WatchlistService service, MedicationQueryService medications, DemoUserProvider users) {
    this.service = service;
    this.medications = medications;
    this.users = users;
  }

  @Operation(summary = "Create a watchlist for the configured demo user")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiModels.WatchlistView create(@Valid @RequestBody ApiModels.CreateWatchlistRequest request) {
    var value = service.create(users.currentUserId(), request.name());
    return ApiModels.WatchlistView.from(value, 0, null);
  }

  @GetMapping
  ApiModels.PageResponse<ApiModels.WatchlistView> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiModels.PageResponse.from(
        service
            .list(users.currentUserId(), page, size)
            .map(
                value ->
                    ApiModels.WatchlistView.from(value, service.itemCount(value.getId()), null)));
  }

  @GetMapping("/{id}")
  ApiModels.WatchlistView get(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int itemPage,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int itemSize) {
    var value = service.get(users.currentUserId(), id, itemPage, itemSize);
    var statuses =
        medications.shortageStatuses(
            value.items().getContent().stream().map(item -> item.getMedication()).toList());
    return ApiModels.WatchlistView.from(
        value.watchlist(),
        value.itemCount(),
        value
            .items()
            .map(
                item ->
                    ApiModels.WatchlistItemView.from(
                        item,
                        statuses.getOrDefault(item.getMedication().getId(), java.util.List.of()))));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    service.delete(users.currentUserId(), id);
  }

  @PostMapping("/{id}/items")
  @ResponseStatus(HttpStatus.CREATED)
  ApiModels.WatchlistItemView addItem(
      @PathVariable UUID id, @Valid @RequestBody ApiModels.AddWatchlistItemRequest request) {
    var item = service.addItem(users.currentUserId(), id, request.drugId());
    var statuses = medications.shortageStatuses(java.util.List.of(item.getMedication()));
    return ApiModels.WatchlistItemView.from(
        item, statuses.getOrDefault(item.getMedication().getId(), java.util.List.of()));
  }

  @DeleteMapping("/{id}/items/{itemId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
    service.removeItem(users.currentUserId(), id, itemId);
  }
}
