package com.yfctransport.materials;

import java.util.UUID;

public interface MaterialRegistry {
  record Snapshot(UUID id, String name, String unit, boolean active) {}

  Snapshot getActive(UUID id);

  Snapshot get(UUID id);
}
