package com.yfctransport.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "rfq_items")
class RfqItem {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID rfqId;

  @Column(nullable = false)
  int position;

  @Column(nullable = false)
  UUID materialId;

  @Column(nullable = false, length = 160)
  String material;

  @Column(nullable = false, precision = 14, scale = 3)
  BigDecimal quantity;

  protected RfqItem() {}
}
