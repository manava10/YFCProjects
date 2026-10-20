package com.yfctransport.fuel;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

class OperatingDtos {
  enum Category {
    TOLL,
    DRIVER_ALLOWANCE,
    PARKING,
    TYRE,
    MAINTENANCE,
    REPAIR,
    OTHER
  }

  record FuelInput(
      UUID entryId,
      @NotNull UUID truckId,
      @NotNull UUID rateId,
      UUID tripId,
      @NotBlank @Size(max = 60) String receiptReference,
      @NotNull LocalDate filledOn,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantityLitres,
      @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
          BigDecimal actualPricePerLitre,
      @DecimalMin("0.0") @Digits(integer = 10, fraction = 1) BigDecimal odometer,
      Instant filledAt,
      @Size(max = 300) String receiptEvidence,
      @Size(max = 1000) String priceVarianceReason) {
    FuelInput {
      if (filledAt != null) filledAt = filledAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
  }

  record ExpenseInput(
      @NotNull UUID expenseId,
      @NotNull UUID truckId,
      UUID tripId,
      @NotNull Category category,
      @NotNull Instant occurredAt,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 1000) String description,
      @NotBlank @Size(max = 100) String receiptReference,
      @Size(max = 300) String receiptEvidence) {
    ExpenseInput {
      if (occurredAt != null)
        occurredAt = occurredAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
  }

  record Decision(
      @NotNull UUID eventId, @Min(1) int revision, @NotBlank @Size(max = 1000) String reason) {}

  record FuelRevision(
      @NotNull UUID eventId,
      @Min(1) int revision,
      @NotBlank @Size(max = 1000) String reason,
      @NotNull UUID rateId,
      @NotNull LocalDate filledOn,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantityLitres,
      @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
          BigDecimal actualPricePerLitre,
      @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 1) BigDecimal odometer,
      @NotNull Instant filledAt,
      @NotBlank @Size(max = 300) String receiptEvidence,
      @Size(max = 1000) String priceVarianceReason) {
    FuelRevision {
      if (filledAt != null) filledAt = filledAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
  }

  record ExpenseRevision(
      @NotNull UUID eventId,
      @Min(1) int revision,
      @NotBlank @Size(max = 1000) String reason,
      @NotNull Category category,
      @NotNull Instant occurredAt,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 1000) String description,
      @NotBlank @Size(max = 300) String receiptEvidence) {
    ExpenseRevision {
      if (occurredAt != null)
        occurredAt = occurredAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
  }

  record Reversal(
      @NotNull UUID reversalId,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {}

  record CorrectRate(
      @NotNull UUID eventId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal expectedPrice,
      @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal newPrice,
      @NotBlank @Size(max = 1000) String reason) {}

  record Review(@NotNull UUID eventId, @NotBlank @Size(max = 1000) String reason) {}

  record OdometerReset(
      @NotNull UUID resetId,
      @NotNull UUID truckId,
      @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 1) BigDecimal newReading,
      @NotNull Instant resetAt,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {
    OdometerReset {
      if (resetAt != null) resetAt = resetAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
  }

  record FuelAllocation(
      @NotNull UUID allocationId,
      @NotNull UUID tripId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantityLitres,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 500) String evidenceReference) {}
}
