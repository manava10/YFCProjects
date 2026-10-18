package com.yfctransport.fleet;

import com.yfctransport.audit.AuditTrail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trucks")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
class TruckController {
  private final TruckRepository trucks;
  private final AuditTrail audit;

  TruckController(TruckRepository trucks, AuditTrail audit) {
    this.trucks = trucks;
    this.audit = audit;
  }

  record CreateTruck(
      @NotBlank @Pattern(regexp = "[A-Z0-9-]{4,20}") String registration,
      @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal capacityTonnes,
      @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3)
          BigDecimal tankCapacityLitres,
      @NotNull @DecimalMin("0.001") @Digits(integer = 5, fraction = 3)
          BigDecimal mileageKmPerLitre) {}

  record TruckView(
      UUID id,
      String registration,
      BigDecimal capacityTonnes,
      BigDecimal tankCapacityLitres,
      BigDecimal mileageKmPerLitre) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @org.springframework.transaction.annotation.Transactional
  TruckView create(@Valid @RequestBody CreateTruck r) {
    Truck t =
        trucks.save(
            new Truck(
                r.registration(),
                r.capacityTonnes(),
                r.tankCapacityLitres(),
                r.mileageKmPerLitre()));
    audit.record("TRUCK_CREATED", "TRUCK", t.id, "Owned truck registered");
    return new TruckView(
        t.id, t.registration, t.capacityTonnes, t.tankCapacityLitres, t.mileageKmPerLitre);
  }
}
