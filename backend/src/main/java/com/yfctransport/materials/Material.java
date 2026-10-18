package com.yfctransport.materials;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "materials")
class Material {
  @Id UUID id = UUID.randomUUID();
  @Version long version;

  @Column(nullable = false, length = 30)
  String code;

  @Column(nullable = false, length = 160)
  String name;

  @Column(nullable = false, length = 30)
  String category;

  @Column(nullable = false, length = 20)
  String unit;

  @Column(length = 8)
  String hsnCode;

  @Column(nullable = false)
  boolean active = true;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected Material() {}
}
