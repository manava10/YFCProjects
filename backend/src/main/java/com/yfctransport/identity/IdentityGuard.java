package com.yfctransport.identity;

import jakarta.persistence.*;

@Entity
@Table(name = "identity_guard")
class IdentityGuard {
  @Id int id;

  protected IdentityGuard() {}
}
