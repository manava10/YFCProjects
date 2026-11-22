package com.yfctransport.orders;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface OrderRepository
    extends JpaRepository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {
  java.util.Optional<SalesOrder> findBySourceRfqId(UUID id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select o from SalesOrder o where o.id=:id")
  java.util.Optional<SalesOrder> lock(UUID id);
}
