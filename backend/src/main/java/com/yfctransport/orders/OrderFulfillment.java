package com.yfctransport.orders;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrderFulfillment {
  record Line(
      UUID id,
      UUID orderId,
      UUID branchId,
      UUID materialId,
      BigDecimal quantity,
      String businessType) {}

  /** Locks the parent order until the caller's transaction completes. */
  Line lockMaterialLine(UUID itemId);

  Line lockLine(UUID itemId);

  void claimTripWorkflow(UUID itemId);

  void recordTripDispatch(UUID tripId, UUID itemId, BigDecimal loaded);

  void recordTripDelivery(UUID tripId, BigDecimal received);

  void correctTripWeights(UUID tripId, BigDecimal loaded, BigDecimal received);
}
