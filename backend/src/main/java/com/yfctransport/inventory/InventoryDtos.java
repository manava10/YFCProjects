package com.yfctransport.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

class InventoryDtos {
  enum Destination {
    YARD,
    DIRECT
  }

  record YardInput(
      @NotNull UUID branchId,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 500) String address) {}

  record YardStatus(@NotNull Boolean active) {}

  record PurchaseInput(
      @NotNull UUID supplierId,
      @NotNull UUID plantId,
      @NotNull UUID materialId,
      @NotNull Destination destination,
      UUID yardId,
      UUID orderItemId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal rate,
      @NotNull LocalDate expectedDate,
      @NotBlank @Size(max = 1000) String terms) {}

  record ReceiptInput(
      @NotNull UUID receiptId,
      @NotNull LocalDate receivedDate,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal loadedQuantity,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal receivedQuantity,
      @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal acceptedQuantity,
      @NotBlank @Size(max = 300) String reference,
      @Size(max = 1000) String discrepancyReason) {}

  record Reason(@NotBlank @Size(max = 1000) String reason) {}

  record ReservationInput(
      @NotNull UUID yardId,
      @NotNull UUID orderItemId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity) {}

  record IssueInput(
      @NotNull UUID movementId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotBlank @Size(max = 300) String reference) {}
}
