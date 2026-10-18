package com.yfctransport.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
class AuditEvent {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  Instant occurredAt = Instant.now();

  @Column(nullable = false, length = 80)
  String actor;

  @Column(nullable = false, length = 60)
  String action;

  @Column(nullable = false, length = 40)
  String targetType;

  UUID targetId;

  @Column(nullable = false, length = 500)
  String detail;

  protected AuditEvent() {}

  AuditEvent(String actor, String action, String targetType, UUID targetId, String detail) {
    this.actor = actor;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.detail = detail;
  }
}
