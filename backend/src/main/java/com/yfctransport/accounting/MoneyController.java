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
class MoneyController {
  enum AccountKind {
    CASH,
    BANK
  }

  enum Kind {
    CUSTOMER_RECEIPT,
    SUPPLIER_PAYMENT,
    CUSTOMER_REFUND,
    SUPPLIER_REFUND
  }

  record Registration(
      @NotNull UUID accountId,
      @NotNull UUID branchId,
      @NotNull AccountKind kind,
      @NotBlank @Size(max = 300) String reference) {}

  record Input(
      @NotNull UUID voucherId,
      @NotNull UUID profileId,
      @NotNull UUID moneyAccountId,
      @NotNull UUID periodId,
      @NotNull UUID branchId,
      @NotNull Kind kind,
      @NotNull LocalDate entryDate,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 300) String reference,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  private final MoneyService service;

  MoneyController(MoneyService service) {
    this.service = service;
  }

  private ResponseEntity<Map<String, Object>> created(Map<String, Object> r) {
    return ResponseEntity.status(Boolean.TRUE.equals(r.get("replayed")) ? 200 : 201).body(r);
  }

  @PostMapping("/money-accounts")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  ResponseEntity<Map<String, Object>> register(@Valid @RequestBody Registration r) {
    return created(service.register(r));
  }

  @GetMapping("/money-accounts")
  List<Map<String, Object>> accounts(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.accounts(page, size);
  }

  @PostMapping("/money-vouchers")
  ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Input r) {
    return created(service.create(r));
  }

  @GetMapping("/money-vouchers")
  List<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @GetMapping("/money-vouchers/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/money-vouchers/{id}/post")
  Map<String, Object> post(@PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.decide(id, r, true);
  }

  @PostMapping("/money-vouchers/{id}/reject")
  Map<String, Object> reject(@PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.decide(id, r, false);
  }

  @PostMapping("/money-vouchers/{id}/reversals")
  ResponseEntity<Map<String, Object>> reverse(
      @PathVariable UUID id, @Valid @RequestBody AccountingDtos.Reversal r) {
    return created(service.reverse(id, r));
  }
}
