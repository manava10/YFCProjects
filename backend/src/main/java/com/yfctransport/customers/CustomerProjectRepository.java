package com.yfctransport.customers;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerProjectRepository extends JpaRepository<CustomerProject, UUID> {
  Page<CustomerProject> findByCustomerId(UUID id, Pageable page);
}
