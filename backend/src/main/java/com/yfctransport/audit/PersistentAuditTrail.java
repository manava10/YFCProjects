package com.yfctransport.audit;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PersistentAuditTrail implements AuditTrail {
  private final AuditRepository events;

  PersistentAuditTrail(AuditRepository events) {
    this.events = events;
  }

  @Transactional
  public void record(String action, String targetType, UUID id, String detail) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    String actor = auth == null || !auth.isAuthenticated() ? "SYSTEM" : auth.getName();
    recordAs(actor, action, targetType, id, detail);
  }

  @Transactional
  public void recordAs(String actor, String action, String targetType, UUID id, String detail) {
    events.save(new AuditEvent(actor, action, targetType, id, detail));
  }
}
