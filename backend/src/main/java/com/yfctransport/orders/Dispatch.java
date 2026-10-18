package com.yfctransport.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "dispatches")
class Dispatch {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID orderId;

  @Column(nullable = false)
  UUID orderItemId;

  @Column(nullable = false, length = 30)
  String reference;

  @Column(nullable = false, precision = 14, scale = 3)
  BigDecimal loadedQuantity;

  @Column(nullable = false, precision = 14, scale = 3)
  BigDecimal receivedQuantity;

  protected Dispatch() {}

  Dispatch(UUID orderId, String reference, BigDecimal loaded, BigDecimal received) {
    this.orderId = orderId;
    this.reference = reference;
    this.loadedQuantity = loaded;
    this.receivedQuantity = received;
  }
}
