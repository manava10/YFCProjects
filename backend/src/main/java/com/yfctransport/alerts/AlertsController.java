package com.yfctransport.alerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class AlertsController {
  private final AlertDigestService service;

  AlertsController(AlertDigestService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  Map<String, Object> summary(
      @RequestParam(defaultValue = "24") @Min(1) @Max(720) int ewayHours,
      @RequestParam(defaultValue = "30") @Min(0) @Max(365) int complianceDays,
      @RequestParam(defaultValue = "1000") @Min(0) @Max(50000) int maintenanceKmMargin,
      @RequestParam(defaultValue = "30") @Min(1) @Max(365) int overdueDays) {
    return service.summary(ewayHours, complianceDays, maintenanceKmMargin, overdueDays);
  }

  @GetMapping("/digests")
  List<Map<String, Object>> digests(
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return service.listDigests(limit);
  }

  @PostMapping("/digest/run")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> runDigest() {
    return service.runDigest(null);
  }
}
