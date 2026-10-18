package com.yfctransport.logistics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class TripController {
  private final TripService trips;

  TripController(TripService trips) {
    this.trips = trips;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> allocate(@Valid @RequestBody TripDtos.Allocate r) {
    var result = trips.allocate(r);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping("/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return trips.get(id);
  }

  @GetMapping
  List<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return trips.list(page, size);
  }

  @PostMapping("/{id}/start-loading")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> start(@PathVariable UUID id, @Valid @RequestBody TripDtos.Event r) {
    return trips.advance(id, "START_LOADING", r);
  }

  @PostMapping("/{id}/load")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> load(@PathVariable UUID id, @Valid @RequestBody TripDtos.Load r) {
    return trips.load(id, r);
  }

  @PostMapping("/{id}/dispatch")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> dispatch(@PathVariable UUID id, @Valid @RequestBody TripDtos.Event r) {
    return trips.advance(id, "DISPATCH", r);
  }

  @PostMapping("/{id}/arrive")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> arrive(@PathVariable UUID id, @Valid @RequestBody TripDtos.Event r) {
    return trips.advance(id, "ARRIVE", r);
  }

  @PostMapping("/{id}/start-unloading")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> unload(@PathVariable UUID id, @Valid @RequestBody TripDtos.Event r) {
    return trips.advance(id, "START_UNLOADING", r);
  }

  @PostMapping("/{id}/deliver")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> deliver(@PathVariable UUID id, @Valid @RequestBody TripDtos.Deliver r) {
    return trips.deliver(id, r);
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> cancel(@PathVariable UUID id, @Valid @RequestBody TripDtos.Cancel r) {
    return trips.cancel(id, r);
  }
}
