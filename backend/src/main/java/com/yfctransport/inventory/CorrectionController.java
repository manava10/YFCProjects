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
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class CorrectionController {
  record ReversalInput(
      @NotNull UUID reversalId,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  record OpeningInput(
      @NotNull UUID openingId,
      @NotNull UUID yardId,
      @NotNull UUID materialId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotNull LocalDate occurredOn,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  record TransferInput(
      @NotNull UUID transferId,
      @NotNull UUID fromYardId,
      @NotNull UUID toYardId,
      @NotNull UUID materialId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotNull LocalDate occurredOn,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  record SupplierReturnInput(
      @NotNull UUID returnId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotNull LocalDate occurredOn,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  record Decision(@NotNull UUID eventId, @NotBlank @Size(max = 1000) String reason) {}

  private final CorrectionService service;

  CorrectionController(CorrectionService service) {
    this.service = service;
  }

  private ResponseEntity<Map<String, Object>> written(Map<String, Object> result) {
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @PostMapping("/stock-adjustments/{id}/reversals")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> reverse(
      @PathVariable UUID id, @Valid @RequestBody ReversalInput r) {
    return written(service.reverse(id, r));
  }

  @GetMapping("/stock-reversals/{id}")
  Map<String, Object> reversal(@PathVariable UUID id) {
    return service.get("stock_reversals", id, "reversal_id", "stock_reversal_events");
  }

  @GetMapping("/stock-reversals")
  List<Map<String, Object>> reversals(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("stock_reversals", page, size);
  }

  @PostMapping("/stock-reversals/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> approveReversal(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideReversal(id, r, true);
  }

  @PostMapping("/stock-reversals/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> rejectReversal(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideReversal(id, r, false);
  }

  @PostMapping("/stock-openings")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> opening(@Valid @RequestBody OpeningInput r) {
    return written(service.opening(r));
  }

  @GetMapping("/stock-openings/{id}")
  Map<String, Object> opening(@PathVariable UUID id) {
    return service.get("stock_openings", id, "opening_id", "stock_opening_events");
  }

  @GetMapping("/stock-openings")
  List<Map<String, Object>> openings(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("stock_openings", page, size);
  }

  @PostMapping("/stock-openings/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> approveOpening(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideOpening(id, r, true);
  }

  @PostMapping("/stock-openings/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> rejectOpening(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideOpening(id, r, false);
  }

  @PostMapping("/stock-transfers")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> transfer(@Valid @RequestBody TransferInput r) {
    return written(service.transfer(r));
  }

  @GetMapping("/stock-transfers/{id}")
  Map<String, Object> transfer(@PathVariable UUID id) {
    return service.get("stock_transfers", id, "transfer_id", "stock_transfer_events");
  }

  @GetMapping("/stock-transfers")
  List<Map<String, Object>> transfers(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("stock_transfers", page, size);
  }

  @PostMapping("/stock-transfers/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> approveTransfer(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideTransfer(id, r, true);
  }

  @PostMapping("/stock-transfers/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> rejectTransfer(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideTransfer(id, r, false);
  }

  @PostMapping("/purchases/{id}/supplier-returns")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> supplierReturn(
      @PathVariable UUID id, @Valid @RequestBody SupplierReturnInput r) {
    return written(service.supplierReturn(id, r));
  }

  @GetMapping("/supplier-returns/{id}")
  Map<String, Object> supplierReturn(@PathVariable UUID id) {
    return service.get("supplier_returns", id, "return_id", "supplier_return_events");
  }

  @GetMapping("/supplier-returns")
  List<Map<String, Object>> supplierReturns(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("supplier_returns", page, size);
  }

  @PostMapping("/supplier-returns/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> approveSupplier(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideSupplier(id, r, true);
  }

  @PostMapping("/supplier-returns/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> rejectSupplier(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideSupplier(id, r, false);
  }

  @PostMapping("/purchase-receipts/{id}/reversals")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> receiptReversal(
      @PathVariable UUID id, @Valid @RequestBody ReversalInput r) {
    return written(service.receiptReversal(id, r));
  }

  @GetMapping("/purchase-receipt-reversals/{id}")
  Map<String, Object> receiptReversal(@PathVariable UUID id) {
    return service.get(
        "purchase_receipt_reversals", id, "reversal_id", "purchase_receipt_reversal_events");
  }

  @GetMapping("/purchase-receipt-reversals")
  List<Map<String, Object>> receiptReversals(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list("purchase_receipt_reversals", page, size);
  }

  @PostMapping("/purchase-receipt-reversals/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> approveReceipt(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideReceipt(id, r, true);
  }

  @PostMapping("/purchase-receipt-reversals/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
  Map<String, Object> rejectReceipt(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decideReceipt(id, r, false);
  }
}
