package com.yfctransport.logistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface TripExpenseContext {
  record Context(
      UUID truckId,
      UUID driverId,
      String status,
      Instant createdAt,
      Instant deliveredAt,
      BigDecimal estimatedFuelLitres) {}

  Context lock(UUID tripId);
}
