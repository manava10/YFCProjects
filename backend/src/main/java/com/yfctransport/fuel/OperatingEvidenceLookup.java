package com.yfctransport.fuel;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class OperatingEvidenceLookup implements OperatingEvidence {
  private final JdbcTemplate db;

  OperatingEvidenceLookup(JdbcTemplate db) {
    this.db = db;
  }

  public void lockFuel(UUID id) {
    lock("fuel_entries", id);
  }

  public void lockExpense(UUID id) {
    lock("operating_expenses", id);
  }

  private void lock(String table, UUID id) {
    var rows =
        db.query(
            "select id from " + table + " where id=? for update",
            (r, n) -> r.getObject(1, UUID.class),
            id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operating record not found");
  }
}
