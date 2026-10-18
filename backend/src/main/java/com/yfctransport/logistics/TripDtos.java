package com.yfctransport.logistics;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

class TripDtos {
  enum Source {
    YARD,
    DIRECT,
    TRANSPORT
  }

  record Allocate(
      @NotNull UUID tripId,
      @NotNull UUID orderItemId,
      @NotNull UUID truckId,
      @NotNull UUID driverId,
      @NotNull Source source,
      UUID reservationId,
      UUID purchaseId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
      @NotNull LocalDate plannedDate,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal distanceKm) {}

  record Event(@NotNull UUID eventId) {}

  record Load(
      @NotNull UUID eventId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal gross,
      @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal tare,
      @NotBlank @Size(max = 100) String slipReference) {}

  record Deliver(
      @NotNull UUID eventId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal gross,
      @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal tare,
      @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal acceptedQuantity,
      @NotBlank @Size(max = 100) String slipReference,
      @NotBlank @Size(max = 160) String receiverName,
      @NotBlank @Size(max = 300) String proofReference,
      @Size(max = 1000) String discrepancyReason) {}

  record Cancel(@NotNull UUID eventId, @NotBlank @Size(max = 1000) String reason) {}
}
