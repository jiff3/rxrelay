package dev.rxrelay.core.api;

import dev.rxrelay.core.config.DemoUserProvider;
import dev.rxrelay.core.service.OverviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/overview")
public class OverviewController {
  private final OverviewQueryService service;
  private final DemoUserProvider users;

  public OverviewController(OverviewQueryService service, DemoUserProvider users) {
    this.service = service;
    this.users = users;
  }

  @Operation(summary = "Get genuine persisted-data and demo-user overview information")
  @GetMapping
  ApiModels.OverviewView overview() {
    return ApiModels.OverviewView.from(service.overview(users.currentUserId()));
  }
}
