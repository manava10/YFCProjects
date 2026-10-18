package com.yfctransport.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "quotation_items")
class QuotationItem {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID quotationId;

  @Column(nullable = false)
  UUID rfqId;

  @Column(nullable = false)
  UUID rfqItemId;

  @Column(nullable = false)
  int position;

  @Column(nullable = false)
  UUID materialId;

  @Column(nullable = false, length = 160)
  String material;

  @Column(nullable = false, precision = 14, scale = 3)
  BigDecimal quantity;

  @Column(nullable = false, precision = 14, scale = 2)
  BigDecimal materialRate;

  @Column(nullable = false, precision = 14, scale = 2)
  BigDecimal transportRate;

  @Column(nullable = false, precision = 26, scale = 2)
  BigDecimal materialAmount;

  @Column(nullable = false, precision = 26, scale = 2)
  BigDecimal transportAmount;

  protected QuotationItem() {}
}
