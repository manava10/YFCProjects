package com.yfctransport.inventory;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import com.yfctransport.materials.MaterialRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
class AdjustmentService implements StockAdjustments {
  private final JdbcTemplate db;
  private final AuditTrail audit;
  private final BranchRegistry branches;
  private final MaterialRegistry materials;
  private final Clock clock;
  private final ObjectMapper json;

  AdjustmentService(
      JdbcTemplate db,
      AuditTrail audit,
      BranchRegistry branches,
      MaterialRegistry materials,
      Clock clock,
      ObjectMapper json) {
    this.db = db;
    this.audit = audit;
    this.branches = branches;
    this.materials = materials;
    this.clock = clock;
    this.json = json;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }

  private Map<String, Object> one(String table, UUID id, boolean lock) {
    var rows =
        db.queryForList("select * from " + table + " where id=?" + (lock ? " for update" : ""), id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
    var result = new LinkedHashMap<String, Object>();
    rows.getFirst().forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = new LinkedHashMap<>(row);
    result.remove("create_payload");
    // Inventory APIs retain their established snake_case field names.
    return result;
  }

  Map<String, Object> get(UUID id) {
    var result = view(one("stock_adjustments", id, false));
    result.put(
        "events",
        db.queryForList(
            "select id,action,reason,actor,occurred_at from stock_adjustment_events where"
                + " adjustment_id=? order by occurred_at,id",
            id));
    return result;
  }

  List<Map<String, Object>> list(UUID trip, int page, int size) {
    return (trip == null
            ? db.queryForList(
                "select * from stock_adjustments order by submitted_at,id limit ? offset ?",
                size,
                (long) page * size)
            : db.queryForList(
                "select * from stock_adjustments where trip_id=? order by submitted_at,id limit ?"
                    + " offset ?",
                trip,
                size,
                (long) page * size))
        .stream()
            .map(
                row -> {
                  var normalized = new LinkedHashMap<String, Object>();
                  row.forEach((k, v) -> normalized.put(k.toLowerCase(Locale.ROOT), v));
                  return view(normalized);
                })
            .toList();
  }

  private Map<String, Object> yard(UUID id, boolean active) {
    var y = one("yards", id, true);
    if (active) {
      if (!Boolean.TRUE.equals(y.get("active"))) throw conflict("Yard inactive");
      branches.requireActive((UUID) y.get("branch_id"));
    }
    return y;
  }

  private Map<String, Object> retry(UUID id, String payload) {
    var rows = db.queryForList("select id from stock_adjustments where id=?", id);
    if (rows.isEmpty()) return null;
    if (!payload.equals(one("stock_adjustments", id, false).get("create_payload")))
      throw conflict("Adjustment ID used with different data");
    var result = get(id);
    result.put("replayed", true);
    return result;
  }

  private void date(LocalDate day) {
    if (day.isAfter(LocalDate.now(clock)))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Physical receipt/count date cannot be in the future");
  }

  private String number(BigDecimal n) {
    return n == null ? null : n.stripTrailingZeros().toPlainString();
  }

  private Map<String, Object> insert(
      UUID id,
      String kind,
      UUID yard,
      UUID material,
      UUID trip,
      BigDecimal qty,
      BigDecimal expected,
      LocalDate day,
      String reason,
      String evidence,
      boolean resalable,
      String payload) {
    db.update(
        "insert into"
            + " stock_adjustments(id,kind,yard_id,material_id,trip_id,quantity,expected_on_hand,occurred_on,reason,evidence_reference,resalable,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        kind,
        yard,
        material,
        trip,
        qty,
        expected,
        day,
        reason,
        evidence,
        resalable,
        actor(),
        payload);
    audit.record("STOCK_ADJUSTMENT_SUBMITTED", "STOCK_ADJUSTMENT", id, "Kind=" + kind);
    var result = get(id);
    result.put("replayed", false);
    return result;
  }

  Map<String, Object> submit(AdjustmentController.Input r) {
    String payload =
        json.writeValueAsString(
            Arrays.asList(
                r.kind(),
                r.yardId(),
                r.materialId(),
                number(r.quantity()),
                number(r.expectedOnHand()),
                r.occurredOn(),
                r.reason(),
                r.evidenceReference()));
    yard(r.yardId(), false);
    var replay = retry(r.adjustmentId(), payload);
    if (replay != null) return replay;
    yard(r.yardId(), true);
    materials.getActive(r.materialId());
    date(r.occurredOn());
    return insert(
        r.adjustmentId(),
        r.kind().name(),
        r.yardId(),
        r.materialId(),
        null,
        r.quantity(),
        r.expectedOnHand(),
        r.occurredOn(),
        r.reason(),
        r.evidenceReference(),
        true,
        payload);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Map<String, Object> submitReturn(
      UUID id,
      UUID trip,
      UUID branch,
      UUID material,
      BigDecimal accepted,
      UUID yardId,
      BigDecimal qty,
      LocalDate day,
      String reason,
      String evidence,
      boolean resalable) {
    String payload =
        json.writeValueAsString(
            Arrays.asList(
                "CUSTOMER_RETURN",
                trip,
                yardId,
                material,
                number(qty),
                day,
                reason,
                evidence,
                resalable));
    var y = yard(yardId, false);
    var replay = retry(id, payload);
    if (replay != null) return replay;
    yard(yardId, true);
    materials.getActive(material);
    date(day);
    if (!branch.equals(y.get("branch_id")))
      throw conflict("Return yard must belong to the order branch");
    BigDecimal used =
        db.queryForObject(
            "select coalesce(sum(quantity),0) from stock_adjustments where trip_id=? and status in"
                + " ('PENDING','APPROVED')",
            BigDecimal.class,
            trip);
    if (used.add(qty).compareTo(accepted) > 0)
      throw conflict("Return quantity exceeds accepted delivery less pending/approved returns");
    return insert(
        id,
        "CUSTOMER_RETURN",
        yardId,
        material,
        trip,
        qty,
        null,
        day,
        reason,
        evidence,
        resalable,
        payload);
  }

  Map<String, Object> decide(UUID id, AdjustmentController.Decision r, boolean approve) {
    var initial = one("stock_adjustments", id, false);
    // Yard is always locked before the adjustment, as in stock receipt/issue workflows.
    var y = yard((UUID) initial.get("yard_id"), false);
    var record = one("stock_adjustments", id, true);
    String action = approve ? "APPROVE" : "REJECT";
    var old = db.queryForList("select id from stock_adjustment_events where id=?", r.eventId());
    if (!old.isEmpty()) {
      var e = one("stock_adjustment_events", r.eventId(), false);
      if (!id.equals(e.get("adjustment_id"))
          || !action.equals(e.get("action"))
          || !r.reason().equals(e.get("reason"))
          || !actor().equals(e.get("actor")))
        throw conflict("Review event ID used with different data or actor");
      var result = get(id);
      result.put("replayed", true);
      return result;
    }
    if (!"PENDING".equals(record.get("status"))) throw conflict("Adjustment already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the adjustment");
    if (approve) {
      if (!Boolean.TRUE.equals(y.get("active"))) throw conflict("Yard inactive");
      branches.requireActive((UUID) y.get("branch_id"));
      materials.getActive((UUID) record.get("material_id"));
      UUID yard = (UUID) record.get("yard_id"), material = (UUID) record.get("material_id");
      if (db.queryForObject(
              "select count(*) from stock_balances where yard_id=? and material_id=?",
              Integer.class,
              yard,
              material)
          == 0)
        db.update("insert into stock_balances(yard_id,material_id) values(?,?)", yard, material);
      var stock =
          db.queryForMap(
              "select on_hand,reserved from stock_balances where yard_id=? and material_id=?",
              yard,
              material);
      BigDecimal onHand = (BigDecimal) stock.get("on_hand"),
          reserved = (BigDecimal) stock.get("reserved"),
          qty = (BigDecimal) record.get("quantity");
      BigDecimal expected = (BigDecimal) record.get("expected_on_hand");
      if (expected != null && onHand.compareTo(expected) != 0)
        throw conflict("Stock changed since count request; reject and submit a fresh count");
      String kind = (String) record.get("kind");
      boolean usable = !kind.equals("CUSTOMER_RETURN") || Boolean.TRUE.equals(record.get("resalable"));
      if (usable) {
        BigDecimal next = kind.equals("DECREASE") ? onHand.subtract(qty) : onHand.add(qty);
        if (next.compareTo(reserved) < 0)
          throw conflict("Adjustment would consume reserved stock or make stock negative");
        db.update(
            "update stock_balances set on_hand=? where yard_id=? and material_id=?",
            next,
            yard,
            material);
      }
    }
    db.update(
        "insert into stock_adjustment_events(id,adjustment_id,action,reason,actor)"
            + " values(?,?,?,?,?)",
        r.eventId(),
        id,
        action,
        r.reason(),
        actor());
    if (approve
        && (!"CUSTOMER_RETURN".equals(record.get("kind"))
            || Boolean.TRUE.equals(record.get("resalable")))) {
      String kind =
          switch ((String) record.get("kind")) {
            case "CUSTOMER_RETURN" -> "RETURN";
            case "INCREASE" -> "ADJUST_IN";
            default -> "ADJUST_OUT";
          };
      db.update(
          "insert into"
              + " stock_adjustment_movements(id,adjustment_id,yard_id,material_id,kind,quantity,reference,recorded_by)"
              + " values(?,?,?,?,?,?,?,?)",
          r.eventId(),
          id,
          record.get("yard_id"),
          record.get("material_id"),
          kind,
          record.get("quantity"),
          "Approved adjustment " + id,
          actor());
    }
    db.update(
        "update stock_adjustments set status=?,reviewed_by=?,reviewed_at=?,review_reason=? where"
            + " id=?",
        approve ? "APPROVED" : "REJECTED",
        actor(),
        java.sql.Timestamp.from(clock.instant()),
        r.reason(),
        id);
    audit.record(
        "STOCK_ADJUSTMENT_" + (approve ? "APPROVED" : "REJECTED"),
        "STOCK_ADJUSTMENT",
        id,
        "Event=" + r.eventId());
    var result = get(id);
    result.put("replayed", false);
    return result;
  }
}
