package com.yfctransport.audit;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AuditController {
  private final AuditRepository events;

  AuditController(AuditRepository events) {
    this.events = events;
  }

  record EventView(
      UUID id,
      Instant occurredAt,
      String actor,
      String action,
      String targetType,
      UUID targetId,
      String detail) {}

  @GetMapping
  Page<EventView> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return events
        .findAll(
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))))
        .map(
            e ->
                new EventView(
                    e.id, e.occurredAt, e.actor, e.action, e.targetType, e.targetId, e.detail));
  }
}
