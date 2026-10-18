package com.yfctransport.suppliers;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlantMaterialRepository extends JpaRepository<PlantMaterial, UUID> {
  Optional<PlantMaterial> findByPlantIdAndMaterialId(UUID plantId, UUID materialId);

  Page<PlantMaterial> findByPlantId(UUID plantId, Pageable page);
}
