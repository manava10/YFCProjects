package com.yfctransport.orders;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuotationRepository extends JpaRepository<Quotation, UUID> {
  @org.springframework.data.jpa.repository.Query("select q.rfqId from Quotation q where q.id=:id")
  Optional<UUID> rfqIdFor(UUID id);

  List<Quotation> findByRfqIdOrderByRevisionDesc(UUID id);
}
