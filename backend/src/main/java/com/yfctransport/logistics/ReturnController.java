package com.yfctransport.logistics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/returns")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
class ReturnController {
  record Input(
      @NotNull UUID returnId,
      @NotNull UUID yardId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotNull LocalDate receivedOn,
      @NotNull Boolean resalable,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  private final ReturnService service;

  ReturnController(ReturnService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<Map<String, Object>> submit(
      @PathVariable UUID tripId, @Valid @RequestBody Input input) {
    var result = service.submit(tripId, input);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }
}
