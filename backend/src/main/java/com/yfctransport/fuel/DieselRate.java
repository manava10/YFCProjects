package com.yfctransport.fuel;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "diesel_rates")
class DieselRate {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false, length = 160)
  String pump;

  @Column(nullable = false, length = 160)
  String location;

  @Column(nullable = false)
  LocalDate effectiveDate;

  @Column(nullable = false, precision = 10, scale = 2)
  BigDecimal pricePerLitre;

  protected DieselRate() {}

  DieselRate(String pump, String location, LocalDate date, BigDecimal price) {
    this.pump = pump;
    this.location = location;
    this.effectiveDate = date;
    this.pricePerLitre = price;
  }
}
