package com.yfctransport.fuel;

import com.yfctransport.audit.AuditTrail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/fuel")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class FuelController {
  private final DieselRateRepository rates;
  private final AuditTrail audit;
  private final OperatingService operating;

  FuelController(DieselRateRepository rates, AuditTrail audit, OperatingService operating) {
    this.rates = rates;
    this.audit = audit;
    this.operating = operating;
  }

  record CreateRate(
      @NotBlank @Size(max = 160) String pump,
      @NotBlank @Size(max = 160) String location,
      @NotNull LocalDate effectiveDate,
      @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal pricePerLitre) {}

  record RateView(
      UUID id, String pump, String location, LocalDate effectiveDate, BigDecimal pricePerLitre) {
    static RateView of(DieselRate r) {
      return new RateView(r.id, r.pump, r.location, r.effectiveDate, r.pricePerLitre);
    }
  }

  @PostMapping("/rates")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  @Transactional
  RateView rate(@Valid @RequestBody CreateRate r) {
    DieselRate created =
        rates.save(
            new DieselRate(
                r.pump().strip(), r.location().strip(), r.effectiveDate(), r.pricePerLitre()));
    audit.record(
        "DIESEL_RATE_CREATED",
        "DIESEL_RATE",
        created.id,
        "Price " + r.pricePerLitre() + " INR/litre, effective " + r.effectiveDate());
    return RateView.of(created);
  }

  @GetMapping("/rates/{id}")
  RateView rate(@PathVariable UUID id) {
    return RateView.of(
        rates
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rate not found")));
  }

  @PostMapping("/entries")
  org.springframework.http.ResponseEntity<java.util.Map<String, Object>> entry(
      @Valid @RequestBody OperatingDtos.FuelInput r) {
    var result = operating.createFuel(r);
    return org.springframework.http.ResponseEntity.status(
            Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping("/entries/{id}")
  java.util.Map<String, Object> entry(@PathVariable UUID id) {
    return operating.get("FUEL", id);
  }
}
