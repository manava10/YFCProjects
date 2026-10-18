package com.yfctransport.branches;

import java.util.UUID;

public interface BranchRegistry {
  void requireActive(UUID id);
}
