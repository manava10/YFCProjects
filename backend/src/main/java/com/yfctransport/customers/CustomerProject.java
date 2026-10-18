package com.yfctransport.customers;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_projects")
class CustomerProject {
  @Id UUID id = UUID.randomUUID();
  @Version long version;

  @Column(nullable = false)
  UUID customerId;

  @Column(nullable = false)
  UUID branchId;

  @Column(nullable = false, length = 30)
  String code;

  @Column(nullable = false, length = 160)
  String name;

  @Column(length = 500)
  String description;

  @Column(nullable = false)
  boolean active = true;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected CustomerProject() {}
}
