package com.yfctransport.orders;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface RfqItemRepository extends JpaRepository<RfqItem, UUID> {
  List<RfqItem> findByRfqIdOrderByPosition(UUID id);
}
