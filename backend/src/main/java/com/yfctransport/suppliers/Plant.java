package com.yfctransport.suppliers;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_plants")
class Plant {
  @Id UUID id = UUID.randomUUID();
  @Version long version;

  @Column(nullable = false)
  UUID supplierId;

  @Column(nullable = false, length = 30)
  String code;

  @Column(nullable = false, length = 160)
  String name;

  @Column(nullable = false, length = 500)
  String address;

  @Column(length = 160)
  String contactName;

  @Column(length = 20)
  String mobile;

  @Column(nullable = false)
  boolean active = true;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected Plant() {}
}
