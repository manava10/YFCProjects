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

  @Column(length = 15)
  String gstin;

  @Column(name = "state_code", length = 2)
  String stateCode;

  @Column(name = "bank_name", length = 160)
  String bankName;

  @Column(name = "bank_account", length = 40)
  String bankAccount;

  @Column(name = "bank_ifsc", length = 15)
  String bankIfsc;

  @Column(nullable = false)
  boolean active = true;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected Branch() {}
}
