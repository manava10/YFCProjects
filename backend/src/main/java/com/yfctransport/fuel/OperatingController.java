package com.yfctransport.fuel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class OperatingController {
  private final OperatingService service;

  OperatingController(OperatingService service) {
    this.service = service;
  }

  @GetMapping("/fuel/entries")
  List<Map<String, Object>> fuel(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("FUEL", page, size);
  }

  @GetMapping("/fuel/entries/{id}/history")
  List<Map<String, Object>> fuelHistory(@PathVariable UUID id) {
    return service.history("FUEL", id);
  }

  @PostMapping("/fuel/entries/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> approveFuel(
      @PathVariable UUID id, @Valid @RequestBody OperatingDtos.Decision r) {
    return service.decide("FUEL", id, true, r);
  }

  @PostMapping("/fuel/entries/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> rejectFuel(
      @PathVariable UUID id, @Valid @RequestBody OperatingDtos.Decision r) {
    return service.decide("FUEL", id, false, r);
  }

  @PostMapping("/fuel/entries/{id}/revisions")
  Map<String, Object> reviseFuel(
      @PathVariable UUID id, @Valid @RequestBody OperatingDtos.FuelRevision r) {
    return service.reviseFuel(id, r);
  }

  @PostMapping("/fuel/rates/{id}/corrections")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  Map<String, Object> correctRate(
      @PathVariable UUID id, @Valid @RequestBody OperatingDtos.CorrectRate r) {
    return service.correctRate(id, r);
  }

  @GetMapping("/fuel/rates/{id}/history")
  List<Map<String, Object>> rateHistory(@PathVariable UUID id) {
    return service.rateHistory(id);
  }

  @PostMapping("/expenses")
  ResponseEntity<Map<String, Object>> expense(@Valid @RequestBody OperatingDtos.ExpenseInput r) {
    var result = service.createExpense(r);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping("/expenses/{id}")
  Map<String, Object> expense(@PathVariable UUID id) {
    return service.get("EXPENSE", id);
  }

  @GetMapping("/expenses")
  List<Map<String, Object>> expenses(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("EXPENSE", page, size);
  }

  @PostMapping("/expenses/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> approveExpense(
      @PathVariable UUID id, @Valid @RequestBody OperatingDtos.Decision r) {
    return service.decide("EXPENSE", id, true, r);
  }

  @PostMapping("/expenses/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> rejectExpense(
      @PathVariable UUID id, @Valid @RequestBody OperatingDtos.Decision r) {
    return service.decide("EXPENSE", id, false, r);
  }

  @PostMapping("/expenses/{id}/revisions")
  Map<String, Object> reviseExpense(
      @PathVariable UUID id, @Valid @RequestBody OperatingDtos.ExpenseRevision r) {
    return service.reviseExpense(id, r);
  }

  @GetMapping("/expenses/{id}/history")
  List<Map<String, Object>> expenseHistory(@PathVariable UUID id) {
    return service.history("EXPENSE", id);
  }

  @GetMapping("/trips/{id}/operating-costs")
  Map<String, Object> costs(@PathVariable UUID id) {
    return service.costs(id);
  }
}
