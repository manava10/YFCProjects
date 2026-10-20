package com.yfctransport.logistics;

import java.util.UUID;

/** Public boundary for attaching evidence to logistics records. */
public interface LogisticsEvidence {
  void lockTrip(UUID id);

  void lockIncident(UUID id);
}
