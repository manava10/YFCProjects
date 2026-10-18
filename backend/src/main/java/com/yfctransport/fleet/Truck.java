package com.yfctransport.fleet;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "trucks")
class Truck {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false, unique = true, length = 20)
  String registration;

  @Column(nullable = false, precision = 10, scale = 3)
  BigDecimal capacityTonnes;

  @Column(nullable = false, precision = 10, scale = 3)
  BigDecimal tankCapacityLitres;

  @Column(nullable = false, precision = 8, scale = 3)
  BigDecimal mileageKmPerLitre;

  protected Truck() {}

  Truck(String registration, BigDecimal capacity, BigDecimal tank, BigDecimal mileage) {
    this.registration = registration;
    this.capacityTonnes = capacity;
    this.tankCapacityLitres = tank;
    this.mileageKmPerLitre = mileage;
  }
}
