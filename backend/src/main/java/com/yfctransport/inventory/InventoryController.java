package com.yfctransport.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class InventoryController {
  private final InventoryService inventory;

  InventoryController(InventoryService inventory) {
    this.inventory = inventory;
  }

  @PostMapping("/yards")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> createYard(@Valid @RequestBody InventoryDtos.YardInput r) {
    return inventory.createYard(r);
  }

  @PatchMapping("/yards/{id}/status")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  Map<String, Object> yardStatus(
      @PathVariable UUID id, @Valid @RequestBody InventoryDtos.YardStatus r) {
    return inventory.yardStatus(id, r.active());
  }

  @GetMapping("/yards")
  List<Map<String, Object>> yards(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return inventory.list("yards", page, size);
  }

  @PostMapping("/purchases")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> createPurchase(@Valid @RequestBody InventoryDtos.PurchaseInput r) {
    return inventory.createPurchase(r);
  }

  @GetMapping("/purchases/{id}")
  Map<String, Object> purchase(@PathVariable UUID id) {
    return inventory.purchase(id);
  }

  @GetMapping("/purchases")
  List<Map<String, Object>> purchases(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return inventory.list("purchase_orders", page, size);
  }

  @PostMapping("/purchases/{id}/close")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> close(@PathVariable UUID id, @Valid @RequestBody InventoryDtos.Reason r) {
    return inventory.closePurchase(id, r.reason());
  }

  @PostMapping("/purchases/{id}/receipts")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> receive(
      @PathVariable UUID id, @Valid @RequestBody InventoryDtos.ReceiptInput r) {
    return written(inventory.receive(id, r));
  }

  @PostMapping("/stock-reservations")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> reserve(@Valid @RequestBody InventoryDtos.ReservationInput r) {
    return inventory.reserve(r);
  }

  @PostMapping("/stock-reservations/{id}/release")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> release(@PathVariable UUID id, @Valid @RequestBody InventoryDtos.Reason r) {
    return inventory.release(id, r.reason());
  }

  @PostMapping("/stock-reservations/{id}/issues")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<Map<String, Object>> issue(
      @PathVariable UUID id, @Valid @RequestBody InventoryDtos.IssueInput r) {
    return written(inventory.issue(id, r));
  }

  @GetMapping("/stock-reservations/{id}")
  Map<String, Object> reservation(@PathVariable UUID id) {
    return inventory.reservation(id);
  }

  @GetMapping("/order-items/{id}/fulfillment")
  Map<String, Object> fulfillment(@PathVariable UUID id) {
    return inventory.fulfillment(id);
  }

  private ResponseEntity<Map<String, Object>> written(Map<String, Object> r) {
    return ResponseEntity.status(
            Boolean.TRUE.equals(r.get("replayed")) ? HttpStatus.OK : HttpStatus.CREATED)
        .body(r);
  }

  @GetMapping("/yards/{yardId}/stock/{materialId}")
  Map<String, Object> balance(@PathVariable UUID yardId, @PathVariable UUID materialId) {
    return inventory.balance(yardId, materialId);
  }

  @GetMapping("/yards/{yardId}/stock/{materialId}/movements")
  List<Map<String, Object>> movements(
      @PathVariable UUID yardId,
      @PathVariable UUID materialId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return inventory.movements(yardId, materialId, page, size);
  }

  @GetMapping("/stock-reservations")
  List<Map<String, Object>> reservations(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return inventory.list("stock_reservations", page, size);
  }
}
