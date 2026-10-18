package com.yfctransport.orders;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, UUID> {
  List<SalesOrderItem> findByOrderIdOrderByPosition(UUID id);
}
