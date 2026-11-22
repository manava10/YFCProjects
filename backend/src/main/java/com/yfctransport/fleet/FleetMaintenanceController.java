package com.yfctransport.fleet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fleet")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class FleetMaintenanceController {
  private final FleetMaintenanceService service;

  FleetMaintenanceController(FleetMaintenanceService service) {
    this.service = service;
  }

  @PostMapping("/maintenance")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> record(@Valid @RequestBody FleetMaintenanceService.CreateLog r) {
    return service.record(r);
  }

  @GetMapping("/maintenance")
  List<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) UUID truckId,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) FleetMaintenanceService.Category category) {
    return service.list(page, size, truckId, branchId, category);
  }

  @GetMapping("/maintenance-due")
  List<Map<String, Object>> due(
      @RequestParam(defaultValue = "30") @Min(0) @Max(365) int days,
      @RequestParam(defaultValue = "1000") @Min(0) @Max(50000) int kmMargin,
      @RequestParam(required = false) UUID branchId) {
    return service.dueAlerts(days, kmMargin, branchId);
  }
}
