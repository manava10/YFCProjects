package com.yfctransport.orders;

import java.math.BigDecimal;
import java.util.UUID;

public interface CustomerCreditCheck {
  void requireWithinLimit(
      UUID customerId, BigDecimal additionalExposure, boolean overrideRequested);
}
