package com.yfctransport.orders;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
class OrderFulfillmentService implements OrderFulfillment {
  private final JdbcTemplate db;

  OrderFulfillmentService(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Line lockMaterialLine(UUID id) {
    var line = lockLine(id);
    if (line.materialId() == null || line.businessType().equals("TRANSPORT"))
      throw new IllegalArgumentException(
          "Fulfillment requires a material sale with saved material and branch");
    return line;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Line lockLine(UUID id) {
    var ids =
        db.query(
            "select order_id from sales_order_items where id=?",
            (r, n) -> r.getObject(1, UUID.class),
            id);
    if (ids.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found");
    UUID order = ids.getFirst();
    db.queryForObject("select id from sales_orders where id=? for update", UUID.class, order);
    return db.queryForObject(
        "select i.id,i.material_id,i.ordered_quantity,o.branch_id,o.business_type from"
            + " sales_order_items i join sales_orders o on o.id=i.order_id where i.id=?",
        (r, n) -> {
          UUID material = r.getObject("material_id", UUID.class),
              branch = r.getObject("branch_id", UUID.class);
          if (branch == null)
            throw new IllegalArgumentException("Trip allocation requires an order branch");
          return new Line(
              id,
              order,
              branch,
              material,
              r.getBigDecimal("ordered_quantity"),
              r.getString("business_type"));
        },
        id);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void claimTripWorkflow(UUID item) {
    lockLine(item);
    String mode =
        db.queryForObject(
            "select fulfillment_mode from sales_order_items where id=?", String.class, item);
    if (mode.equals("LEGACY")
        && db.queryForObject(
                "select count(*) from dispatches where order_item_id=?", Integer.class, item)
            > 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This item has legacy dispatch records; reconcile them before using trips");
    db.update("update sales_order_items set fulfillment_mode='TRIP' where id=?", item);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordTripDispatch(UUID trip, UUID item, java.math.BigDecimal loaded) {
    var line = lockLine(item);
    String reference = "T" + trip.toString().replace("-", "").substring(0, 29);
    db.update(
        "insert into"
            + " dispatches(id,order_id,order_item_id,reference,loaded_quantity,received_quantity)"
            + " values(?,?,?,?,?,0)",
        trip,
        line.orderId(),
        item,
        reference,
        loaded);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordTripDelivery(UUID trip, java.math.BigDecimal received) {
    if (db.update("update dispatches set received_quantity=? where id=?", received, trip) != 1)
      throw new IllegalStateException("Trip dispatch record is missing");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void correctTripWeights(
      UUID trip, java.math.BigDecimal loaded, java.math.BigDecimal received) {
    if (db.update(
            "update dispatches set loaded_quantity=?,received_quantity=? where id=?",
            loaded,
            received,
            trip)
        != 1) throw new IllegalStateException("Trip dispatch record is missing");
  }
}
