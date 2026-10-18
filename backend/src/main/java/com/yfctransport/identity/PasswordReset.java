package com.yfctransport.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_resets")
class PasswordReset {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID accountId;

  @Column(nullable = false, unique = true, length = 64)
  String tokenHash;

  @Column(nullable = false)
  Instant expiresAt;

  @Column(nullable = false)
  boolean used = false;

  protected PasswordReset() {}

  PasswordReset(UUID accountId, String tokenHash, Instant expiresAt) {
    this.accountId = accountId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }
}
