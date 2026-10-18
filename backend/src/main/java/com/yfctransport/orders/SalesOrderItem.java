package com.yfctransport.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "sales_order_items")
class SalesOrderItem {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID orderId;

  @Column(nullable = false, length = 10)
  String fulfillmentMode = "LEGACY";

  UUID sourceRfqItemId;
  UUID materialId;

  @Column(nullable = false)
  int position;

  @Column(nullable = false, length = 160)
  String material;

  @Column(nullable = false, precision = 14, scale = 3)
  BigDecimal orderedQuantity;

  @Column(nullable = false, precision = 14, scale = 2)
  BigDecimal materialRate;

  @Column(nullable = false, precision = 14, scale = 2)
  BigDecimal transportRate;

  @Column(nullable = false, precision = 26, scale = 2)
  BigDecimal materialAmount;

  @Column(nullable = false, precision = 26, scale = 2)
  BigDecimal transportAmount;

  protected SalesOrderItem() {}
}
