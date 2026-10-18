package com.yfctransport.fuel;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fuel_entries")
class FuelEntry {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false)
  UUID truckId;

  @Column(nullable = false)
  UUID rateId;

  @Column(nullable = false, length = 60)
  String receiptReference;

  @Column(nullable = false)
  LocalDate filledOn;

  @Column(nullable = false, precision = 12, scale = 3)
  BigDecimal quantityLitres;

  @Column(nullable = false, precision = 10, scale = 2)
  BigDecimal actualPricePerLitre;

  @Column(nullable = false, precision = 26, scale = 2)
  BigDecimal amount;

  protected FuelEntry() {}

  FuelEntry(
      UUID truckId,
      UUID rateId,
      String receipt,
      LocalDate date,
      BigDecimal quantity,
      BigDecimal price) {
    this.truckId = truckId;
    this.rateId = rateId;
    this.receiptReference = receipt;
    this.filledOn = date;
    this.quantityLitres = quantity;
    this.actualPricePerLitre = price;
    this.amount = quantity.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP);
  }
}
