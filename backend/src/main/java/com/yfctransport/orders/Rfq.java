package com.yfctransport.orders;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "rfqs")
class Rfq {
  @Id UUID id = UUID.randomUUID();
  @Version long version;

  @Column(nullable = false)
  UUID customerId;

  @Column(nullable = false)
  UUID branchId;

  @Column(nullable = false)
  UUID projectId;

  @Column(nullable = false)
  UUID deliverySiteId;

  @Column(nullable = false, length = 500)
  String deliveryAddress;

  @Column(nullable = false, length = 30)
  String businessType;

  @Column(nullable = false)
  LocalDate requiredDate;

  @Column(length = 2000)
  String remarks;

  @Column(nullable = false, length = 20)
  String status = "OPEN";

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected Rfq() {}
}
