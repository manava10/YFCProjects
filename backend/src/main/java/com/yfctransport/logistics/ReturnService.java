package com.yfctransport.logistics;

import com.yfctransport.inventory.StockAdjustments;
import com.yfctransport.orders.OrderFulfillment;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class ReturnService {
  private final JdbcTemplate db;
  private final TripExpenseContext trips;
  private final OrderFulfillment orders;
  private final StockAdjustments stock;

  ReturnService(
      JdbcTemplate db, TripExpenseContext trips, OrderFulfillment orders, StockAdjustments stock) {
    this.db = db;
    this.trips = trips;
    this.orders = orders;
    this.stock = stock;
  }

  Map<String, Object> submit(UUID trip, ReturnController.Input r) {
    var context = trips.lock(trip);
    if (!"DELIVERED".equals(context.status()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Customer returns require a delivered trip");
    var row =
        db.queryForMap("select order_item_id,accepted_quantity,source from trips where id=?", trip);
    if ("TRANSPORT".equals(row.get("source")))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Transport-only goods cannot become YFC-owned return stock");
    var line = orders.lockMaterialLine((UUID) row.get("order_item_id"));
    if (r.receivedOn()
        .isBefore(context.deliveredAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Return receipt date cannot precede delivery");
    return stock.submitReturn(
        r.returnId(),
        trip,
        line.branchId(),
        line.materialId(),
        (BigDecimal) row.get("accepted_quantity"),
        r.yardId(),
        r.quantity(),
        r.receivedOn(),
        r.reason(),
        r.evidenceReference(),
        r.resalable());
  }
}
