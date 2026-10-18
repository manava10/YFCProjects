package com.yfctransport.orders;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "quotations")
class Quotation {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID rfqId;

  @Column(nullable = false)
  int revision;

  @Column(nullable = false, length = 20)
  String status = "DRAFT";

  @Column(nullable = false)
  LocalDate validUntil;

  @Column(nullable = false, length = 1000)
  String paymentTerms;

  @Column(nullable = false, length = 1000)
  String deliveryTerms;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  Instant sentAt;
  Instant decidedAt;

  @Column(length = 80)
  String decidedBy;

  @Column(length = 160)
  String customerContact;

  @Column(length = 300)
  String approvalReference;

  @Column(length = 1000)
  String decisionNote;

  protected Quotation() {}
}
