package com.yfctransport.audit;

import java.util.UUID;

public interface AuditTrail {
  void recordAs(String actor, String action, String targetType, UUID targetId, String detail);

  void record(String action, String targetType, UUID targetId, String detail);
}
