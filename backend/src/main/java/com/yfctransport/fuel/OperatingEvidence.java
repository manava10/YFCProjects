package com.yfctransport.fuel;

import java.util.UUID;

/** Public boundary for attaching evidence to fuel and expense records. */
public interface OperatingEvidence {
  void lockFuel(UUID id);

  void lockExpense(UUID id);
}
