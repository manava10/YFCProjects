package com.yfctransport.orders;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuotationItemRepository extends JpaRepository<QuotationItem, UUID> {
  List<QuotationItem> findByQuotationIdOrderByPosition(UUID id);
}
