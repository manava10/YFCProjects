package com.yfctransport.inventory;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import com.yfctransport.materials.MaterialRegistry;
import com.yfctransport.orders.OrderFulfillment;
import com.yfctransport.suppliers.SupplierRegistry;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class InventoryService implements TripSupply {
  private final JdbcTemplate db;
  private final BranchRegistry branches;
  private final MaterialRegistry materials;
  private final SupplierRegistry suppliers;
  private final OrderFulfillment orders;
  private final AuditTrail audit;
  private final Clock clock;

  InventoryService(
      JdbcTemplate db,
      BranchRegistry branches,
      MaterialRegistry materials,
      SupplierRegistry suppliers,
      OrderFulfillment orders,
      AuditTrail audit,
      Clock clock) {
    this.db = db;
    this.branches = branches;
    this.materials = materials;
    this.suppliers = suppliers;
    this.orders = orders;
    this.audit = audit;
    this.clock = clock;
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private BigDecimal number(Map<String, Object> row, String key) {
    return (BigDecimal) row.get(key);
  }

  private UUID uuid(Map<String, Object> row, String key) {
    return (UUID) row.get(key);
  }

  private Map<String, Object> one(String table, UUID id, boolean lock) {
    var rows =
        db.queryForList("select * from " + table + " where id=?" + (lock ? " for update" : ""), id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
    // Use stable lower-case keys across PostgreSQL and H2; values remain exact JDBC decimals.
    var result = new LinkedHashMap<String, Object>();
    rows.getFirst().forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Map<String, Object> yard(UUID id, boolean requireActive) {
    var y = one("yards", id, true);
    if (requireActive) {
      if (!Boolean.TRUE.equals(y.get("active"))) throw conflict("Yard is inactive");
      branches.requireActive(uuid(y, "branch_id"));
    }
    return y;
  }

  Map<String, Object> createYard(InventoryDtos.YardInput r) {
    branches.requireActive(r.branchId());
    UUID id = UUID.randomUUID();
    db.update(
        "insert into yards(id,branch_id,code,name,address) values(?,?,?,?,?)",
        id,
        r.branchId(),
        r.code().toUpperCase(Locale.ROOT),
        r.name().strip(),
        r.address().strip());
    audit.record("YARD_CREATED", "YARD", id, "Yard created");
    return one("yards", id, false);
  }

  Map<String, Object> yardStatus(UUID id, boolean active) {
    var y = yard(id, false);
    if (active) branches.requireActive(uuid(y, "branch_id"));
    db.update("update yards set active=? where id=?", active, id);
    audit.record("YARD_STATUS_CHANGED", "YARD", id, "Active=" + active);
    return one("yards", id, false);
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(String table, int page, int size) {
    // Table names come only from controller constants, never user input.
    return normalized(
        db.queryForList(
            "select * from " + table + " order by created_at desc,id desc limit ? offset ?",
            size,
            (long) page * size));
  }

  @Transactional(readOnly = true)
  Map<String, Object> purchase(UUID id) {
    var p = one("purchase_orders", id, false);
    p.put("currency", "INR");
    p.put("unit", "TONNE");
    p.put("taxTreatment", "EXCLUDED_PENDING_ACCOUNTING");
    p.put(
        "receipts",
        normalized(
            db.queryForList(
                "select * from purchase_receipts where purchase_id=? order by created_at,id", id)));
    return p;
  }

  @Transactional(readOnly = true)
  Map<String, Object> reservation(UUID id) {
    var r = one("stock_reservations", id, false);
    r.put(
        "remaining_quantity",
        number(r, "quantity")
            .subtract(number(r, "issued_quantity"))
            .subtract(number(r, "released_quantity")));
    return r;
  }

  Map<String, Object> fulfillment(UUID id) {
    var line = orders.lockMaterialLine(id);
    BigDecimal allocated = allocated(id);
    BigDecimal directAccepted =
        db.queryForObject(
            "select coalesce(sum(accepted_quantity),0) from purchase_orders where order_item_id=?",
            BigDecimal.class,
            id);
    BigDecimal yardIssued =
        db.queryForObject(
            "select coalesce(sum(issued_quantity),0) from stock_reservations where order_item_id=?",
            BigDecimal.class,
            id);
    return Map.of(
        "orderItemId",
        id,
        "orderedQuantity",
        line.quantity(),
        "allocatedQuantity",
        allocated,
        "unallocatedQuantity",
        line.quantity().subtract(allocated),
        "directAcceptedQuantity",
        directAccepted,
        "yardIssuedQuantity",
        yardIssued,
        "unit",
        "TONNE",
        "billingBasis",
        "ORDERED_QUANTITY");
  }

  private BigDecimal allocated(UUID line) {
    BigDecimal direct =
        db.queryForObject(
            "select coalesce(sum(case when status='OPEN' then"
                + " quantity-received_quantity+accepted_quantity else accepted_quantity end),0)"
                + " from purchase_orders where order_item_id=? and status<>'CANCELLED'",
            BigDecimal.class,
            line);
    BigDecimal yard =
        db.queryForObject(
            "select coalesce(sum(quantity-released_quantity),0) from stock_reservations where"
                + " order_item_id=?",
            BigDecimal.class,
            line);
    return direct.add(yard);
  }

  private void capacity(OrderFulfillment.Line line, BigDecimal quantity) {
    if (allocated(line.id()).add(quantity).compareTo(line.quantity()) > 0)
      throw conflict("Allocation exceeds the order item's remaining quantity");
  }

  Map<String, Object> createPurchase(InventoryDtos.PurchaseInput r) {
    if (r.expectedDate().isBefore(LocalDate.now(clock)))
      throw new IllegalArgumentException("Expected date must be today or later");
    var m = materials.getActive(r.materialId());
    var source = suppliers.requireActiveSource(r.supplierId(), r.plantId(), r.materialId());
    UUID branch;
    if (r.destination() == InventoryDtos.Destination.YARD) {
      if (r.yardId() == null || r.orderItemId() != null)
        throw new IllegalArgumentException("YARD requires yardId and no orderItemId");
      branch = uuid(yard(r.yardId(), true), "branch_id");
    } else {
      if (r.orderItemId() == null || r.yardId() != null)
        throw new IllegalArgumentException("DIRECT requires orderItemId and no yardId");
      var line = orders.lockMaterialLine(r.orderItemId());
      if (!line.materialId().equals(r.materialId()))
        throw new IllegalArgumentException("Purchase material differs from order item");
      branches.requireActive(line.branchId());
      capacity(line, r.quantity());
      branch = line.branchId();
    }
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " purchase_orders(id,supplier_id,plant_id,material_id,branch_id,supplier_name,plant_name,material_name,destination,yard_id,order_item_id,quantity,rate,subtotal,expected_date,terms)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        r.supplierId(),
        r.plantId(),
        r.materialId(),
        branch,
        source.supplier(),
        source.plant(),
        m.name(),
        r.destination().name(),
        r.yardId(),
        r.orderItemId(),
        r.quantity(),
        r.rate(),
        r.quantity().multiply(r.rate()).setScale(2, RoundingMode.HALF_UP),
        r.expectedDate(),
        r.terms().strip());
    audit.record(
        "PURCHASE_CREATED",
        "PURCHASE",
        id,
        "Supplier purchase issued; destination=" + r.destination());
    return purchase(id);
  }

  Map<String, Object> closePurchase(UUID id, String reason) {
    // Global lock order: sales order -> purchase -> yard. Scalar lookup avoids stale pre-lock
    // state.
    var lineIds =
        db.query(
            "select order_item_id from purchase_orders where id=?",
            (r, n) -> r.getObject(1, UUID.class),
            id);
    if (!lineIds.isEmpty() && lineIds.getFirst() != null)
      orders.lockMaterialLine(lineIds.getFirst());
    var p = one("purchase_orders", id, true);
    if (!p.get("status").equals("OPEN")) throw conflict("Only an open purchase may be closed");
    if (db.queryForObject(
            "select count(*) from trip_supply_claims where purchase_id=? and status in"
                + " ('CLAIMED','IN_TRANSIT')",
            Integer.class,
            id)
        > 0) throw conflict("Purchase has active trips; finish or cancel them before closing");
    String status = number(p, "received_quantity").signum() == 0 ? "CANCELLED" : "CLOSED";
    db.update(
        "update purchase_orders set status=?,close_reason=? where id=?",
        status,
        reason.strip(),
        id);
    audit.record(
        "PURCHASE_CLOSED", "PURCHASE", id, "Status=" + status + "; reason saved on purchase");
    return purchase(id);
  }

  private void ensureStock(UUID yard, UUID material) {
    // Yard lock serializes row creation, reservations, receipts and issues, including zero-stock
    // races.
    Integer count =
        db.queryForObject(
            "select count(*) from stock_balances where yard_id=? and material_id=?",
            Integer.class,
            yard,
            material);
    if (count == 0)
      db.update("insert into stock_balances(yard_id,material_id) values(?,?)", yard, material);
  }

  Map<String, Object> receive(UUID id, InventoryDtos.ReceiptInput r) {
    return receiveInternal(id, r, false);
  }

  private Map<String, Object> receiveInternal(
      UUID id, InventoryDtos.ReceiptInput r, boolean tripReceipt) {
    var p = one("purchase_orders", id, true);
    var existing = db.queryForList("select id from purchase_receipts where id=?", r.receiptId());
    if (!existing.isEmpty()) {
      var old = one("purchase_receipts", r.receiptId(), false);
      boolean same =
          id.equals(uuid(old, "purchase_id"))
              && r.receivedDate().equals(((java.sql.Date) old.get("received_date")).toLocalDate())
              && number(old, "loaded_quantity").compareTo(r.loadedQuantity()) == 0
              && number(old, "received_quantity").compareTo(r.receivedQuantity()) == 0
              && number(old, "accepted_quantity").compareTo(r.acceptedQuantity()) == 0
              && r.reference().strip().equals(old.get("reference"))
              && Objects.equals(clean(r.discrepancyReason()), old.get("discrepancy_reason"));
      if (!same) throw conflict("Receipt ID was already used with different details");
      old.put("replayed", true);
      return old;
    }
    if (!tripReceipt
        && db.queryForObject(
                "select count(*) from trip_supply_claims where purchase_id=? and status in"
                    + " ('CLAIMED','IN_TRANSIT')",
                Integer.class,
                id)
            > 0)
      throw conflict("Record delivery through the active trip instead of a standalone receipt");
    if (!p.get("status").equals("OPEN")) throw conflict("Purchase is not open for receipts");
    if (r.receivedDate().isAfter(LocalDate.now(clock)))
      throw new IllegalArgumentException("Receipt date cannot be in the future");
    if (r.acceptedQuantity().compareTo(r.receivedQuantity()) > 0)
      throw new IllegalArgumentException("Accepted quantity exceeds received quantity");
    if ((r.loadedQuantity().compareTo(r.receivedQuantity()) != 0
            || r.receivedQuantity().compareTo(r.acceptedQuantity()) != 0)
        && clean(r.discrepancyReason()) == null)
      throw new IllegalArgumentException("Weight differences require a discrepancy reason");
    BigDecimal received = number(p, "received_quantity").add(r.receivedQuantity());
    if (received.compareTo(number(p, "quantity")) > 0)
      throw conflict("Receipts exceed purchased quantity");
    UUID yard = uuid(p, "yard_id"), material = uuid(p, "material_id");
    // Existing purchases can be received after a supplier/material is deactivated; yard must be
    // operational.
    if (yard != null) {
      yard(yard, true);
      ensureStock(yard, material);
    }
    db.update(
        "insert into"
            + " purchase_receipts(id,purchase_id,received_date,loaded_quantity,received_quantity,accepted_quantity,reference,discrepancy_reason,recorded_by)"
            + " values(?,?,?,?,?,?,?,?,?)",
        r.receiptId(),
        id,
        r.receivedDate(),
        r.loadedQuantity(),
        r.receivedQuantity(),
        r.acceptedQuantity(),
        r.reference().strip(),
        clean(r.discrepancyReason()),
        actor());
    db.update(
        "update purchase_orders set"
            + " received_quantity=?,accepted_quantity=accepted_quantity+?,status=? where id=?",
        received,
        r.acceptedQuantity(),
        received.compareTo(number(p, "quantity")) == 0 ? "RECEIVED" : "OPEN",
        id);
    if (yard != null && r.acceptedQuantity().signum() > 0) {
      db.update(
          "update stock_balances set on_hand=on_hand+? where yard_id=? and material_id=?",
          r.acceptedQuantity(),
          yard,
          material);
      db.update(
          "insert into"
              + " stock_movements(id,yard_id,material_id,kind,quantity,receipt_id,reference,recorded_by)"
              + " values(?,?,?,'RECEIPT',?,?,?,?)",
          UUID.randomUUID(),
          yard,
          material,
          r.acceptedQuantity(),
          r.receiptId(),
          r.reference().strip(),
          actor());
    }
    audit.record("PURCHASE_RECEIVED", "PURCHASE", id, "Receipt=" + r.receiptId());
    var result = one("purchase_receipts", r.receiptId(), false);
    result.put("replayed", false);
    return result;
  }

  private List<Map<String, Object>> normalized(List<Map<String, Object>> rows) {
    return rows.stream()
        .map(
            row -> {
              Map<String, Object> result = new LinkedHashMap<>();
              row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
              return result;
            })
        .toList();
  }

  private String clean(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  Map<String, Object> reserve(InventoryDtos.ReservationInput r) {
    var line = orders.lockMaterialLine(r.orderItemId());
    var y = yard(r.yardId(), true);
    if (!line.branchId().equals(uuid(y, "branch_id")))
      throw new IllegalArgumentException("Yard and order must belong to the same branch");
    materials.getActive(line.materialId());
    capacity(line, r.quantity());
    ensureStock(r.yardId(), line.materialId());
    var b = balance(r.yardId(), line.materialId());
    if (number(b, "on_hand").subtract(number(b, "reserved")).compareTo(r.quantity()) < 0)
      throw conflict("Insufficient available stock");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into stock_reservations(id,yard_id,material_id,order_item_id,quantity)"
            + " values(?,?,?,?,?)",
        id,
        r.yardId(),
        line.materialId(),
        line.id(),
        r.quantity());
    db.update(
        "update stock_balances set reserved=reserved+? where yard_id=? and material_id=?",
        r.quantity(),
        r.yardId(),
        line.materialId());
    audit.record("STOCK_RESERVED", "RESERVATION", id, "Order item=" + line.id());
    return one("stock_reservations", id, false);
  }

  private Map<String, Object> lockReservation(UUID id) {
    var items =
        db.query(
            "select order_item_id from stock_reservations where id=?",
            (r, n) -> r.getObject(1, UUID.class),
            id);
    if (items.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
    orders.lockMaterialLine(items.getFirst());
    // A reservation is read only after its owning order lock; then yard lock serializes stock
    // changes.
    var r = one("stock_reservations", id, false);
    yard(uuid(r, "yard_id"), false);
    return r;
  }

  Map<String, Object> release(UUID id, String reason) {
    var r = lockReservation(id);
    BigDecimal remaining =
        number(r, "quantity")
            .subtract(number(r, "issued_quantity"))
            .subtract(number(r, "released_quantity"));
    if (held(r).signum() > 0)
      throw conflict("Reservation has allocated trips; cancel those trips before releasing stock");
    if (remaining.signum() == 0) throw conflict("Reservation has no quantity left to release");
    db.update(
        "update stock_reservations set released_quantity=released_quantity+?,release_reason=? where"
            + " id=?",
        remaining,
        reason.strip(),
        id);
    db.update(
        "update stock_balances set reserved=reserved-? where yard_id=? and material_id=?",
        remaining,
        uuid(r, "yard_id"),
        uuid(r, "material_id"));
    audit.record(
        "STOCK_RELEASED",
        "RESERVATION",
        id,
        "Unissued balance released; reason saved on reservation");
    return one("stock_reservations", id, false);
  }

  Map<String, Object> issue(UUID id, InventoryDtos.IssueInput input) {
    return issueInternal(id, input, null);
  }

  private Map<String, Object> issueInternal(UUID id, InventoryDtos.IssueInput input, UUID trip) {
    var r = lockReservation(id);
    var existing = db.queryForList("select id from stock_movements where id=?", input.movementId());
    if (!existing.isEmpty()) {
      var m = one("stock_movements", input.movementId(), false);
      if (!id.equals(uuid(m, "reservation_id"))
          || number(m, "quantity").compareTo(input.quantity()) != 0
          || !input.reference().strip().equals(m.get("reference")))
        throw conflict("Movement ID was already used with different details");
      m.put("replayed", true);
      return m;
    }
    yard(uuid(r, "yard_id"), true);
    BigDecimal remaining =
        number(r, "quantity")
            .subtract(number(r, "issued_quantity"))
            .subtract(number(r, "released_quantity"));
    BigDecimal unavailable = held(r);
    if (trip != null)
      unavailable =
          unavailable.subtract(
              db.queryForObject(
                  "select quantity from trip_supply_claims where trip_id=? and status='CLAIMED'",
                  BigDecimal.class,
                  trip));
    if (input.quantity().compareTo(remaining.subtract(unavailable)) > 0)
      throw conflict("Issue exceeds reservation's remaining quantity");
    db.update(
        "update stock_reservations set issued_quantity=issued_quantity+? where id=?",
        input.quantity(),
        id);
    db.update(
        "update stock_balances set on_hand=on_hand-?,reserved=reserved-? where yard_id=? and"
            + " material_id=?",
        input.quantity(),
        input.quantity(),
        uuid(r, "yard_id"),
        uuid(r, "material_id"));
    db.update(
        "insert into"
            + " stock_movements(id,yard_id,material_id,kind,quantity,reservation_id,reference,recorded_by)"
            + " values(?,?,?,'ISSUE',?,?,?,?)",
        input.movementId(),
        uuid(r, "yard_id"),
        uuid(r, "material_id"),
        input.quantity(),
        id,
        input.reference().strip(),
        actor());
    audit.record("STOCK_ISSUED", "RESERVATION", id, "Movement=" + input.movementId());
    var m = one("stock_movements", input.movementId(), false);
    m.put("replayed", false);
    return m;
  }

  private BigDecimal held(Map<String, Object> reservation) {
    return db.queryForObject(
        "select coalesce(sum(quantity),0) from trip_supply_claims where reservation_id=? and"
            + " status='CLAIMED'",
        BigDecimal.class,
        uuid(reservation, "id"));
  }

  @Override
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void claim(UUID trip, UUID item, UUID reservation, UUID purchase, BigDecimal quantity) {
    orders.lockMaterialLine(item);
    if ((reservation == null) == (purchase == null))
      throw new IllegalArgumentException("Exactly one trip supply source is required");
    if (reservation != null) {
      var r = lockReservation(reservation);
      yard(uuid(r, "yard_id"), true);
      if (!item.equals(uuid(r, "order_item_id")))
        throw new IllegalArgumentException("Reservation belongs to a different order item");
      BigDecimal available =
          number(r, "quantity")
              .subtract(number(r, "issued_quantity"))
              .subtract(number(r, "released_quantity"))
              .subtract(held(r));
      if (quantity.compareTo(available) > 0)
        throw conflict("Trip exceeds unclaimed reservation quantity");
    } else {
      var p = one("purchase_orders", purchase, true);
      if (!"DIRECT".equals(p.get("destination")) || !item.equals(uuid(p, "order_item_id")))
        throw new IllegalArgumentException("Direct purchase belongs to a different order item");
      if (!"OPEN".equals(p.get("status"))) throw conflict("Direct purchase is not open");
      BigDecimal held =
          db.queryForObject(
              "select coalesce(sum(quantity),0) from trip_supply_claims where purchase_id=? and"
                  + " status in ('CLAIMED','IN_TRANSIT')",
              BigDecimal.class,
              purchase);
      if (quantity.compareTo(
              number(p, "quantity").subtract(number(p, "received_quantity")).subtract(held))
          > 0) throw conflict("Trip exceeds unclaimed purchase quantity");
    }
    db.update(
        "insert into"
            + " trip_supply_claims(trip_id,order_item_id,reservation_id,purchase_id,quantity,status)"
            + " values(?,?,?,?,?,'CLAIMED')",
        trip,
        item,
        reservation,
        purchase,
        quantity);
  }

  private Map<String, Object> supply(UUID trip) {
    var ids =
        db.query(
            "select order_item_id from trip_supply_claims where trip_id=?",
            (r, n) -> r.getObject(1, UUID.class),
            trip);
    if (ids.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip supply not found");
    orders.lockMaterialLine(ids.getFirst());
    var rows = db.queryForList("select * from trip_supply_claims where trip_id=? for update", trip);
    return normalized(rows).getFirst();
  }

  @Override
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void dispatch(UUID trip, BigDecimal loaded, String reference) {
    var c = supply(trip);
    if (!"CLAIMED".equals(c.get("status")) || loaded.compareTo(number(c, "quantity")) > 0)
      throw conflict("Invalid trip supply dispatch");
    UUID reservation = uuid(c, "reservation_id");
    if (reservation != null)
      issueInternal(reservation, new InventoryDtos.IssueInput(trip, loaded, reference), trip);
    db.update(
        "update trip_supply_claims set quantity=?,status='IN_TRANSIT' where trip_id=?",
        loaded,
        trip);
  }

  @Override
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void deliver(
      UUID trip,
      BigDecimal loaded,
      BigDecimal received,
      BigDecimal accepted,
      LocalDate date,
      String reference,
      String reason) {
    var c = supply(trip);
    if (!"IN_TRANSIT".equals(c.get("status"))) throw conflict("Trip supply is not in transit");
    UUID purchase = uuid(c, "purchase_id");
    if (purchase != null)
      receiveInternal(
          purchase,
          new InventoryDtos.ReceiptInput(trip, date, loaded, received, accepted, reference, reason),
          true);
    db.update("update trip_supply_claims set status='DELIVERED' where trip_id=?", trip);
  }

  @Override
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void cancel(UUID trip) {
    var c = supply(trip);
    if (!"CLAIMED".equals(c.get("status")))
      throw conflict("Dispatched stock cannot be cancelled; a controlled return is required");
    db.update("update trip_supply_claims set status='CANCELLED' where trip_id=?", trip);
  }

  @Transactional(readOnly = true)
  Map<String, Object> balance(UUID yard, UUID material) {
    one("yards", yard, false);
    materials.get(material);
    var rows =
        db.queryForList(
            "select on_hand,reserved,on_hand-reserved as available from stock_balances where"
                + " yard_id=? and material_id=?",
            yard,
            material);
    var result = new LinkedHashMap<String, Object>();
    if (rows.isEmpty()) {
      result.put("on_hand", BigDecimal.ZERO);
      result.put("reserved", BigDecimal.ZERO);
      result.put("available", BigDecimal.ZERO);
    } else rows.getFirst().forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    result.put("yardId", yard);
    result.put("materialId", material);
    result.put("unit", "TONNE");
    return result;
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> movements(UUID yard, UUID material, int page, int size) {
    return normalized(
        db.queryForList(
            "select * from stock_movements where yard_id=? and material_id=? order by created_at"
                + " desc,id desc limit ? offset ?",
            yard,
            material,
            size,
            (long) page * size));
  }
}
