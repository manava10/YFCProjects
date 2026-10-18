package com.yfctransport.fleet;

import java.util.UUID;

public interface TruckRegistry {
  boolean exists(UUID id);

  java.math.BigDecimal lockTankCapacity(UUID id);
}
