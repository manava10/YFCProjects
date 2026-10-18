package com.yfctransport.customers;

import java.util.UUID;

public interface CustomerLocations {
  record Location(UUID siteId, UUID projectId, UUID branchId, String name, String address) {}

  Location getActive(UUID customerId, UUID siteId);
}
