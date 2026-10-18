package com.yfctransport.fleet;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class TruckService implements TruckRegistry {
  private final TruckRepository trucks;
  private final org.springframework.jdbc.core.JdbcTemplate db;

  TruckService(TruckRepository trucks, org.springframework.jdbc.core.JdbcTemplate db) {
    this.trucks = trucks;
    this.db = db;
  }

  public boolean exists(UUID id) {
    return trucks.existsById(id);
  }

  @Override
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public java.math.BigDecimal lockTankCapacity(UUID id) {
    var values =
        db.query(
            "select tank_capacity_litres from trucks where id=? for update",
            (r, n) -> r.getBigDecimal(1),
            id);
    if (values.isEmpty())
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "Truck not found");
    return values.getFirst();
  }
}
