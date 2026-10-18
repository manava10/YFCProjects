package com.yfctransport.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
class Account {
  @Id UUID id = UUID.randomUUID();
  @Version long version;

  @Column(nullable = false, unique = true, length = 80)
  String username;

  @Column(nullable = false, length = 120)
  String displayName;

  @Column(nullable = false, length = 255)
  String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  Role role;

  @Column(nullable = false)
  boolean enabled = true;

  @Column(nullable = false)
  long authVersion = 0;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  protected Account() {}

  Account(String username, String displayName, String passwordHash, Role role) {
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.role = role;
  }
}
