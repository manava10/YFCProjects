package com.yfctransport.customers;

import java.util.UUID;

public interface CustomerRegistry {
  boolean exists(UUID id);
}
