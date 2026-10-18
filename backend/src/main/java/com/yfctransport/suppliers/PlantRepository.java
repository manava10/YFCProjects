package com.yfctransport.suppliers;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlantRepository extends JpaRepository<Plant, UUID> {
  Page<Plant> findBySupplierId(UUID id, Pageable page);
}
