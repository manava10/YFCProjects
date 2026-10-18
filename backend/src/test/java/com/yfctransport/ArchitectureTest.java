package com.yfctransport;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {
  @Test
  void modulesRespectBoundaries() {
    ApplicationModules.of(YfcApplication.class).verify();
  }
}
