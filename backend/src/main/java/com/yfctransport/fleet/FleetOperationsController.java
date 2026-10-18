package com.yfctransport.fleet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
class FleetOperationsController {
  private final FleetOperations fleet;

  FleetOperationsController(FleetOperations fleet) {
    this.fleet = fleet;
  }

  @PutMapping("/trucks/{id}/compliance")
  Map<String, Object> compliance(
      @PathVariable UUID id, @Valid @RequestBody FleetOperations.Compliance r) {
    return fleet.compliance(id, r);
  }

  @GetMapping("/trucks/{id}")
  Map<String, Object> truck(@PathVariable UUID id) {
    return fleet.get("trucks", id);
  }

  @GetMapping("/trucks")
  List<Map<String, Object>> trucks(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return fleet.list("trucks", page, size);
  }

  @GetMapping("/fleet/compliance-alerts")
  List<Map<String, Object>> alerts(
      @RequestParam(required = false) UUID branchId,
      @RequestParam(defaultValue = "30") @Min(0) @Max(365) int days,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return fleet.alerts(branchId, days, page, size);
  }

  @PostMapping("/drivers")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> driver(@Valid @RequestBody FleetOperations.DriverInput r) {
    return fleet.driver(null, r);
  }

  @PutMapping("/drivers/{id}")
  Map<String, Object> update(
      @PathVariable UUID id, @Valid @RequestBody FleetOperations.DriverInput r) {
    return fleet.driver(id, r);
  }

  @GetMapping("/drivers/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return fleet.get("drivers", id);
  }

  @GetMapping("/drivers")
  List<Map<String, Object>> drivers(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return fleet.list("drivers", page, size);
  }
}
