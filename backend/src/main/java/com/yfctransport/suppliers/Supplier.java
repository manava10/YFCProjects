package com.yfctransport.suppliers;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
class Supplier {
  @Id UUID id = UUID.randomUUID();
  @Version long version;

  @Column(nullable = false, length = 30)
  String code;

  @Column(nullable = false, length = 160)
  String name;

  @Column(nullable = false, length = 15)
  String gstin;

  @Column(nullable = false, length = 20)
  String mobile;

  @Column(length = 254)
  String email;

  @Column(nullable = false, length = 500)
  String address;

  @Column(nullable = false)
  boolean active = true;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected Supplier() {}
}
