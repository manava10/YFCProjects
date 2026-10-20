package com.yfctransport.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Logistics holds the source order/trip lock before submitting a physical return. */
public interface StockAdjustments {
  Map<String, Object> submitReturn(
      UUID id,
      UUID trip,
      UUID branch,
      UUID material,
      BigDecimal deliveredAccepted,
      UUID yard,
      BigDecimal quantity,
      LocalDate receivedOn,
      String reason,
      String evidence,
      boolean resalable);
}
