package com.yfctransport.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

interface IdentityGuardRepository extends JpaRepository<IdentityGuard, Integer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from IdentityGuard g where g.id=1")
  IdentityGuard acquire();
}
