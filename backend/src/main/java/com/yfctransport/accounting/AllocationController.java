package com.yfctransport.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/allocations")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
class AllocationController {
  record Input(
      @NotNull UUID allocationId,
      @NotNull UUID voucherId,
      @NotNull UUID preparationId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
      @Size(max = 1000) String note) {}

  private final AllocationService service;

  AllocationController(AllocationService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<Map<String, Object>> allocate(@Valid @RequestBody Input r) {
    var result = service.allocate(r);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping
  List<Map<String, Object>> list(
      @RequestParam(required = false) UUID voucherId,
      @RequestParam(required = false) UUID preparationId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(voucherId, preparationId, status, page, size);
  }

  @GetMapping("/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/reverse")
  Map<String, Object> reverse(
      @PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.reverse(id, r);
  }
}
