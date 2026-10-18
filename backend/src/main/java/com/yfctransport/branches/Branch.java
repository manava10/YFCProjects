package com.yfctransport.branches;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branches")
class Branch {
  @Id UUID id = UUID.randomUUID();
  @Version long version;

  @Column(nullable = false, length = 30)
  String code;

  @Column(nullable = false, length = 160)
  String name;

  @Column(nullable = false, length = 500)
  String address;

  @Column(nullable = false, length = 100)
  String city;

  @Column(nullable = false, length = 100)
  String state;

  @Column(nullable = false, length = 6)
  String pincode;

  @Column(nullable = false)
  boolean active = true;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected Branch() {}
}
