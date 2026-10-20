package com.yfctransport.orders;

import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class OrderBillingLookup implements OrderBilling {
  private final JdbcTemplate db;
  private final Clock clock;

  OrderBillingLookup(JdbcTemplate db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Snapshot snapshot(UUID id) {
    var rows =
        db.query(
            "select o.*,c.name as customer_name from sales_orders o join customers c on"
                + " c.id=o.customer_id where o.id=?",
            (r, n) ->
                new Snapshot(
                    id,
                    r.getObject("customer_id", UUID.class),
                    r.getObject("branch_id", UUID.class),
                    r.getString("customer_name"),
                    r.getString("business_type"),
                    r.getString("delivery_site"),
                    r.getString("payment_terms"),
                    r.getString("delivery_terms"),
                    r.getTimestamp("created_at").toInstant().atZone(clock.getZone()).toLocalDate(),
                    null),
            id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order not found");
    var h = rows.getFirst();
    var lines =
        db.query(
            "select i.*,coalesce((select sum(d.loaded_quantity) from dispatches d where"
                + " d.order_item_id=i.id),0) as loaded,coalesce((select sum(d.received_quantity)"
                + " from dispatches d where d.order_item_id=i.id),0) as received from"
                + " sales_order_items i where i.order_id=? order by i.position",
            (r, n) ->
                new Line(
                    r.getObject("id", UUID.class),
                    r.getString("material"),
                    r.getBigDecimal("ordered_quantity"),
                    r.getBigDecimal("loaded"),
                    r.getBigDecimal("received"),
                    r.getBigDecimal("material_rate"),
                    r.getBigDecimal("transport_rate")),
            id);
    return new Snapshot(
        h.orderId(),
        h.customerId(),
        h.branchId(),
        h.customerName(),
        h.businessType(),
        h.deliveryAddress(),
        h.paymentTerms(),
        h.deliveryTerms(),
        h.createdOn(),
        lines);
  }
}
