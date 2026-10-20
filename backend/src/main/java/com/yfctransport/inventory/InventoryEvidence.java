package com.yfctransport.inventory;

import java.util.UUID;

/** Public boundary for attaching evidence to inventory correction records. */
public interface InventoryEvidence {
  void lockAdjustment(UUID id);

  void lockTransfer(UUID id);

  void lockOpening(UUID id);
}
