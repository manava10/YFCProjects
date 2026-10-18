package com.yfctransport.suppliers;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "plant_materials")
class PlantMaterial {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID plantId;

  @Column(nullable = false)
  UUID materialId;

  protected PlantMaterial() {}

  PlantMaterial(UUID plantId, UUID materialId) {
    this.plantId = plantId;
    this.materialId = materialId;
  }
}
