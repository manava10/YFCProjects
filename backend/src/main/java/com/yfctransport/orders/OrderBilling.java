package com.yfctransport.orders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only commercial source for billing preparation; prices exclude tax. */
public interface OrderBilling {
  record Line(
      UUID itemId,
      String material,
      BigDecimal orderedQuantity,
      BigDecimal loadedQuantity,
      BigDecimal receivedQuantity,
      BigDecimal materialRate,
      BigDecimal transportRate) {}

  record Snapshot(
      UUID orderId,
      UUID customerId,
      UUID branchId,
      String customerName,
      String businessType,
      String deliveryAddress,
      String paymentTerms,
      String deliveryTerms,
      LocalDate createdOn,
      List<Line> lines) {}

  Snapshot snapshot(UUID orderId);
}
