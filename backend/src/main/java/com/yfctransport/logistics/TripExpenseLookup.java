package com.yfctransport.logistics;

import com.yfctransport.orders.OrderFulfillment;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
class TripExpenseLookup implements TripExpenseContext {
  private final JdbcTemplate db;
  private final OrderFulfillment orders;

  TripExpenseLookup(JdbcTemplate db, OrderFulfillment orders) {
    this.db = db;
    this.orders = orders;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Context lock(UUID id) {
    var items =
        db.query(
            "select order_item_id from trips where id=?", (r, n) -> r.getObject(1, UUID.class), id);
    if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    orders.lockLine(items.getFirst());
    return db.queryForObject(
        "select * from trips where id=? for update",
        (r, n) ->
            new Context(
                r.getObject("truck_id", UUID.class),
                r.getObject("driver_id", UUID.class),
                r.getString("status"),
                r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("delivered_at") == null
                    ? null
                    : r.getTimestamp("delivered_at").toInstant(),
                r.getBigDecimal("estimated_fuel_litres")),
        id);
  }
}
