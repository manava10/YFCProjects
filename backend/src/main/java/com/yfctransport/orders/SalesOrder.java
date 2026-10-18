package com.yfctransport.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_orders")
class SalesOrder {
  @Id UUID id = UUID.randomUUID();
  @Version long version;

  UUID branchId;
  UUID projectId;
  UUID deliverySiteId;
  UUID materialId;
  UUID sourceRfqId;
  UUID sourceQuotationId;
  java.time.LocalDate requiredDate;

  @Column(length = 1000)
  String paymentTerms;

  @Column(length = 1000)
  String deliveryTerms;

  @Column(nullable = false)
  UUID customerId;

  @Column(nullable = false, length = 30)
  String businessType;

  @Column(nullable = false, length = 160)
  String material;

  @Column(nullable = false, length = 500)
  String deliverySite;

  @Column(nullable = false, precision = 14, scale = 3)
  BigDecimal orderedQuantity;

  @Column(nullable = false, precision = 14, scale = 2)
  BigDecimal unitRate;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected SalesOrder() {}

  SalesOrder(
      UUID customerId,
      String businessType,
      String material,
      String deliverySite,
      BigDecimal quantity,
      BigDecimal rate) {
    this.customerId = customerId;
    this.businessType = businessType;
    this.material = material;
    this.deliverySite = deliverySite;
    this.orderedQuantity = quantity;
    this.unitRate = rate;
  }
}
