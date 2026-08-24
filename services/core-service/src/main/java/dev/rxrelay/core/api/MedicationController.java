package dev.rxrelay.core.api;

import dev.rxrelay.core.domain.ShortageStatus;
import dev.rxrelay.core.service.MedicationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/drugs")
public class MedicationController {
  private final MedicationQueryService service;

  public MedicationController(MedicationQueryService service) {
    this.service = service;
  }

  @Operation(summary = "Search normalized medication identities")
  @GetMapping
  ApiModels.PageResponse<ApiModels.DrugView> search(
      @RequestParam(defaultValue = "") @Size(max = 120) String query,
      @RequestParam(required = false) ShortageStatus status,
      @RequestParam(defaultValue = "") @Size(max = 200) String manufacturer,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
      @RequestParam(defaultValue = "name,asc") @Size(max = 32) String sort) {
    return ApiModels.PageResponse.from(
        service
            .search(query, status, manufacturer, page, size, sort)
            .map(ApiModels.DrugView::from));
  }

  @Operation(summary = "Get one normalized medication identity")
  @GetMapping("/{id}")
  ApiModels.DrugView get(@PathVariable UUID id) {
    return ApiModels.DrugView.from(service.get(id));
  }

  @Operation(summary = "Get source shortage records for a drug")
  @GetMapping("/{id}/shortages")
  ApiModels.PageResponse<ApiModels.ShortageView> shortages(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiModels.PageResponse.from(
        service.shortages(id, page, size).map(ApiModels.ShortageView::from));
  }

  @Operation(summary = "Get meaningful status changes for a drug")
  @GetMapping("/{id}/timeline")
  ApiModels.PageResponse<ApiModels.TimelineView> timeline(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiModels.PageResponse.from(
        service.timeline(id, page, size).map(ApiModels.TimelineView::from));
  }
}
