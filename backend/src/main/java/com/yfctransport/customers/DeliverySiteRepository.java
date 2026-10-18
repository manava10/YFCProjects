package com.yfctransport.customers;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface DeliverySiteRepository extends JpaRepository<DeliverySite, UUID> {
  Page<DeliverySite> findByProjectId(UUID id, Pageable page);
}
