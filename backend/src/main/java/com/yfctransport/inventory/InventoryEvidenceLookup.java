package com.yfctransport.inventory;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class InventoryEvidenceLookup implements InventoryEvidence {
  private final JdbcTemplate db;

  InventoryEvidenceLookup(JdbcTemplate db) {
    this.db = db;
  }

  public void lockAdjustment(UUID id) {
    lock("stock_adjustments", id, "Stock adjustment not found");
  }

  public void lockTransfer(UUID id) {
    lock("stock_transfers", id, "Stock transfer not found");
  }

  public void lockOpening(UUID id) {
    lock("stock_openings", id, "Stock opening not found");
  }

  private void lock(String table, UUID id, String missing) {
    var rows =
        db.query(
            "select id from " + table + " where id=? for update",
            (r, n) -> r.getObject(1, UUID.class),
            id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, missing);
  }
}
