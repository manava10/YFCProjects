package com.yfctransport.logistics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/incidents")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class IncidentController {
  enum Category {
    BREAKDOWN,
    ACCIDENT,
    DELAY,
    SHORTAGE,
    DAMAGE,
    OTHER
  }

  record Report(
      @NotNull UUID incidentId,
      @NotNull Category category,
      @NotNull Instant occurredAt,
      @NotBlank @Size(max = 2000) String description,
      @NotBlank @Size(max = 500) String evidenceReference) {
    Report {
      if (occurredAt != null) occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
    }
  }

  record Resolution(
      @NotNull UUID eventId,
      @NotBlank @Size(max = 2000) String resolution,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  private final IncidentService service;

  IncidentController(IncidentService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> report(
      @PathVariable UUID tripId, @Valid @RequestBody Report report) {
    var result = service.report(tripId, report);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping
  List<Map<String, Object>> list(
      @PathVariable UUID tripId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(tripId, page, size);
  }

  @GetMapping("/{id}")
  Map<String, Object> get(@PathVariable UUID tripId, @PathVariable UUID id) {
    return service.get(tripId, id);
  }

  @PostMapping("/{id}/resolve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> resolve(
      @PathVariable UUID tripId, @PathVariable UUID id, @Valid @RequestBody Resolution resolution) {
    return service.resolve(tripId, id, resolution);
  }
}
