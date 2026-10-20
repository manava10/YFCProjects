package com.yfctransport.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
class PartyFinanceController {
  enum Type {
    CUSTOMER,
    SUPPLIER
  }

  record Input(
      @NotNull UUID profileId,
      @NotNull Type partyType,
      @NotNull UUID partyId,
      @NotNull UUID accountId,
      @NotNull @Min(0) @Max(365) Integer paymentTermsDays,
      @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal creditLimit) {}

  record Terms(
      @NotNull UUID eventId,
      @Min(1) int version,
      @NotNull @Min(0) @Max(365) Integer paymentTermsDays,
      @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal creditLimit,
      @NotBlank @Size(max = 1000) String reason) {}

  private final PartyFinanceService service;

  PartyFinanceController(PartyFinanceService service) {
    this.service = service;
  }

  @PostMapping("/parties")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Input r) {
    var result = service.create(r);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping("/parties")
  List<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @GetMapping("/parties/by-customer/{id}")
  Map<String, Object> customer(@PathVariable UUID id) {
    return service.lookup(Type.CUSTOMER, id);
  }

  @GetMapping("/parties/by-supplier/{id}")
  Map<String, Object> supplier(@PathVariable UUID id) {
    return service.lookup(Type.SUPPLIER, id);
  }

  @GetMapping("/parties/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/parties/{id}/terms")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  Map<String, Object> terms(@PathVariable UUID id, @Valid @RequestBody Terms r) {
    return service.terms(id, r);
  }

  @GetMapping("/party-balances")
  List<Map<String, Object>> balances(
      @RequestParam LocalDate asOf,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.balances(asOf, branchId, page, size);
  }

  @GetMapping("/parties/{id}/ledger")
  Map<String, Object> ledger(
      @PathVariable UUID id,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.ledger(id, from, to, branchId, page, size);
  }
}
