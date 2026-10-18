package com.yfctransport.fleet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface FleetAssignments {
  record Vehicle(String registration, BigDecimal capacity, BigDecimal mileage) {}

  Vehicle requireTruck(UUID id, UUID branch, LocalDate date);

  String requireDriver(UUID id, UUID branch, LocalDate date);

  void assign(UUID trip, UUID truck, UUID driver);

  void release(UUID trip);
}
