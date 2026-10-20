package com.yfctransport.suppliers;

import java.util.UUID;

public interface SupplierRegistry {
  record Source(String supplier, String plant) {}

  String name(UUID id);

  Source requireActiveSource(UUID supplierId, UUID plantId, UUID materialId);
}
