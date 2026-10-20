package com.yfctransport.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stock-adjustments")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class AdjustmentController {
  enum Kind {
    INCREASE,
    DECREASE
  }

  record Input(
      @NotNull UUID adjustmentId,
      @NotNull Kind kind,
      @NotNull UUID yardId,
      @NotNull UUID materialId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotNull @DecimalMin("0.000") @Digits(integer = 16, fraction = 3) BigDecimal expectedOnHand,
      @NotNull LocalDate occurredOn,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  record Decision(@NotNull UUID eventId, @NotBlank @Size(max = 1000) String reason) {}

  private final AdjustmentService service;

  AdjustmentController(AdjustmentService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody Input r) {
    var result = service.submit(r);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping("/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping
  List<Map<String, Object>> list(
      @RequestParam(required = false) UUID tripId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(tripId, page, size);
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> approve(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decide(id, r, true);
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> reject(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decide(id, r, false);
  }
}
