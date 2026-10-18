package com.yfctransport.orders;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

interface RfqRepository extends JpaRepository<Rfq, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Rfq r where r.id=:id")
  Optional<Rfq> lock(UUID id);
}
