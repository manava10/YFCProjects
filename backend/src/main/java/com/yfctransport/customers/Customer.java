package com.yfctransport.customers;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
class Customer {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false, length = 160)
  String name;

  @Column(nullable = false, length = 20)
  String mobile;

  @Column(length = 15)
  String gstin;

  @Column(length = 500)
  String address;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected Customer() {}

  Customer(String name, String mobile, String gstin) {
    this(name, mobile, gstin, null);
  }

  Customer(String name, String mobile, String gstin, String address) {
    this.name = name;
    this.mobile = mobile;
    this.gstin = gstin;
    this.address = address == null || address.isBlank() ? null : address.strip();
  }
}
