package com.yfctransport.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/billing-preparations")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
class BillingPreparationController {
  enum Kind {
    SALES_ORDER,
    PURCHASE_RECEIPT
  }

  enum Basis {
    ORDERED,
    LOADED,
    RECEIVED,
    ACCEPTED
  }

  record Input(
      @NotNull UUID preparationId,
      @NotNull Kind kind,
      @NotNull UUID sourceId,
      @NotNull LocalDate preparedOn,
      @NotNull Basis quantityBasis,
      @NotBlank @Size(max = 300) String reference,
      @NotBlank @Size(max = 500) String evidenceReference,
      @NotBlank @Size(max = 1000) String basisReason) {}

  record JournalInput(
      @NotNull UUID journalId,
      @NotNull UUID periodId,
      @NotNull LocalDate entryDate,
      @NotNull UUID counterAccountId,
      @NotBlank @Size(max = 1000) String description,
      @NotBlank @Size(max = 300) String reference) {}

  private final BillingPreparationService service;

  BillingPreparationController(BillingPreparationService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Input r) {
    var result = service.create(r);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping
  List<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) Kind kind,
      @RequestParam(required = false) UUID partyId,
      @RequestParam(required = false) UUID branchId) {
    return service.list(page, size, kind, partyId, branchId);
  }

  @GetMapping("/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/ready")
  Map<String, Object> ready(@PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.decide(id, r, "READY");
  }

  @PostMapping("/{id}/reject")
  Map<String, Object> reject(@PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.decide(id, r, "REJECT");
  }

  @PostMapping("/{id}/cancel")
  Map<String, Object> cancel(@PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.decide(id, r, "CANCEL");
  }

  @PostMapping("/{id}/journals")
  ResponseEntity<Map<String, Object>> journal(
      @PathVariable UUID id, @Valid @RequestBody JournalInput r) {
    var result = service.journal(id, r);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }
}
