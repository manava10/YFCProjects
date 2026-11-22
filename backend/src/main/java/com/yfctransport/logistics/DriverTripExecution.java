package com.yfctransport.logistics;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Public SPI allowing the driver portal to advance assigned trips through the standard state machine. */
public interface DriverTripExecution {
  Map<String, Object> startLoading(UUID tripId, UUID eventId);

  Map<String, Object> load(
      UUID tripId, UUID eventId, BigDecimal gross, BigDecimal tare, String slipReference);

  Map<String, Object> dispatch(UUID tripId, UUID eventId);

  Map<String, Object> arrive(UUID tripId, UUID eventId);

  Map<String, Object> startUnloading(UUID tripId, UUID eventId);

  Map<String, Object> deliver(
      UUID tripId,
      UUID eventId,
      BigDecimal gross,
      BigDecimal tare,
      BigDecimal acceptedQuantity,
      String slipReference,
      String receiverName,
      String proofReference,
      String discrepancyReason);
}
