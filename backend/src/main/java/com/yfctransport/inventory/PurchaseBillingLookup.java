package com.yfctransport.inventory;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PurchaseBillingLookup implements PurchaseBilling {
  private final JdbcTemplate db;

  PurchaseBillingLookup(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  @Transactional(readOnly = true)
  public Receipt receipt(UUID id) {
    var rows =
        db.query(
            "select r.*,p.supplier_id,p.branch_id,p.supplier_name,p.material_name,p.rate,p.terms"
                + " from purchase_receipts r join purchase_orders p on p.id=r.purchase_id where"
                + " r.id=?",
            (r, n) ->
                new Receipt(
                    id,
                    r.getObject("purchase_id", UUID.class),
                    r.getObject("supplier_id", UUID.class),
                    r.getObject("branch_id", UUID.class),
                    r.getString("supplier_name"),
                    r.getString("material_name"),
                    r.getString("terms"),
                    r.getObject("received_date", LocalDate.class),
                    r.getBigDecimal("loaded_quantity"),
                    r.getBigDecimal("received_quantity"),
                    r.getBigDecimal("accepted_quantity"),
                    r.getBigDecimal("rate"),
                    r.getString("reference")),
            id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase receipt not found");
    return rows.getFirst();
  }
}
