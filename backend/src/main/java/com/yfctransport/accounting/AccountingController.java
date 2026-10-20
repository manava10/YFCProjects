package com.yfctransport.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
class AccountingController {
  private final AccountingService service;

  AccountingController(AccountingService service) {
    this.service = service;
  }

  private ResponseEntity<Map<String, Object>> created(Map<String, Object> result) {
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @PostMapping("/accounts")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  ResponseEntity<Map<String, Object>> account(@Valid @RequestBody AccountingDtos.Account r) {
    return created(service.account(r));
  }

  @GetMapping("/accounts")
  List<Map<String, Object>> accounts(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("ledger_accounts", page, size);
  }

  @GetMapping("/accounts/{id}")
  Map<String, Object> account(@PathVariable UUID id) {
    return service.account(id);
  }

  @PatchMapping("/accounts/{id}/status")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  Map<String, Object> status(
      @PathVariable UUID id, @Valid @RequestBody AccountingDtos.AccountStatus r) {
    return service.status(id, r);
  }

  @PostMapping("/periods")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  ResponseEntity<Map<String, Object>> period(@Valid @RequestBody AccountingDtos.Period r) {
    return created(service.period(r));
  }

  @GetMapping("/periods")
  List<Map<String, Object>> periods(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("financial_periods", page, size);
  }

  @PostMapping("/periods/{id}/close")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  Map<String, Object> close(@PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.close(id, r);
  }

  @PostMapping("/journals")
  ResponseEntity<Map<String, Object>> journal(@Valid @RequestBody AccountingDtos.Journal r) {
    return created(service.journal(r));
  }

  @GetMapping("/journals")
  List<Map<String, Object>> journals(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("journals", page, size);
  }

  @GetMapping("/journals/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return service.journal(id);
  }

  @PostMapping("/journals/{id}/post")
  Map<String, Object> post(@PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.decide(id, r, true);
  }

  @PostMapping("/journals/{id}/reject")
  Map<String, Object> reject(@PathVariable UUID id, @Valid @RequestBody AccountingDtos.Decision r) {
    return service.decide(id, r, false);
  }

  @PostMapping("/journals/{id}/reversals")
  ResponseEntity<Map<String, Object>> reverse(
      @PathVariable UUID id, @Valid @RequestBody AccountingDtos.Reversal r) {
    return created(service.reverse(id, r));
  }

  @GetMapping("/trial-balance")
  Map<String, Object> trial(
      @RequestParam LocalDate asOf, @RequestParam(required = false) UUID branchId) {
    return service.trial(asOf, branchId);
  }

  @GetMapping("/accounts/{id}/ledger")
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
