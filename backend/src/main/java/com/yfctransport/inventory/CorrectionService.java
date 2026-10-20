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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
class CorrectionService {
  private final JdbcTemplate db;
  private final BranchRegistry branches;
  private final MaterialRegistry materials;
  private final AuditTrail audit;
  private final Clock clock;
  private final ObjectMapper json;

  CorrectionService(
      JdbcTemplate db,
      BranchRegistry branches,
      MaterialRegistry materials,
      AuditTrail audit,
      Clock clock,
      ObjectMapper json) {
    this.db = db;
    this.branches = branches;
    this.materials = materials;
    this.audit = audit;
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
    return result;
  }

  Map<String, Object> get(String table, UUID id, String eventKey, String events) {
    var result = view(one(table, id, false));
    result.put(
        "events",
        db.queryForList(
            "select id,action,reason,actor,occurred_at from "
                + events
                + " where "
                + eventKey
                + "=? order by occurred_at,id",
            id));
    return result;
  }

  List<Map<String, Object>> list(String table, int page, int size) {
    return db
        .queryForList(
            "select * from " + table + " order by submitted_at,id limit ? offset ?",
            size,
            (long) page * size)
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

  private void date(LocalDate day) {
    if (day.isAfter(LocalDate.now(clock)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date cannot be in the future");
  }

  private String number(BigDecimal n) {
    return n.stripTrailingZeros().toPlainString();
  }

  private Map<String, Object> replay(String table, UUID id, String payload) {
    if (db.queryForObject("select count(*) from " + table + " where id=?", Integer.class, id) == 0)
      return null;
    if (!payload.equals(one(table, id, false).get("create_payload")))
      throw conflict("Record ID used with different data");
    var getter =
        switch (table) {
          case "stock_reversals" -> get(table, id, "reversal_id", "stock_reversal_events");
          case "stock_openings" -> get(table, id, "opening_id", "stock_opening_events");
          case "stock_transfers" -> get(table, id, "transfer_id", "stock_transfer_events");
          case "purchase_receipt_reversals" ->
              get(table, id, "reversal_id", "purchase_receipt_reversal_events");
          default -> get(table, id, "return_id", "supplier_return_events");
        };
    getter.put("replayed", true);
    return getter;
  }

  private void ensureStock(UUID yard, UUID material) {
    if (db.queryForObject(
            "select count(*) from stock_balances where yard_id=? and material_id=?",
            Integer.class,
            yard,
            material)
        == 0)
      db.update("insert into stock_balances(yard_id,material_id) values(?,?)", yard, material);
  }

  private Map<String, Object> stock(UUID yard, UUID material) {
    ensureStock(yard, material);
    return oneBalance(yard, material);
  }

  private Map<String, Object> oneBalance(UUID yard, UUID material) {
    var raw =
        db.queryForMap(
            "select on_hand,reserved from stock_balances where yard_id=? and material_id=?",
            yard,
            material);
    var result = new LinkedHashMap<String, Object>();
    raw.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private void apply(UUID yard, UUID material, BigDecimal delta) {
    var s = oneBalance(yard, material);
    BigDecimal next = ((BigDecimal) s.get("on_hand")).add(delta);
    if (next.compareTo((BigDecimal) s.get("reserved")) < 0)
      throw conflict("Change would consume reserved stock or make stock negative");
    db.update(
        "update stock_balances set on_hand=? where yard_id=? and material_id=?",
        next,
        yard,
        material);
  }

  private void move(
      UUID event, String type, UUID source, UUID yard, UUID material, String kind, BigDecimal qty, String ref) {
    db.update(
        "insert into"
            + " stock_correction_movements(id,source_type,source_id,yard_id,material_id,kind,quantity,reference,recorded_by)"
            + " values(?,?,?,?,?,?,?,?,?)",
        event,
        type,
        source,
        yard,
        material,
        kind,
        qty,
        ref,
        actor());
  }

  private boolean replayEvent(
      String table, UUID event, UUID target, String targetKey, String action, String reason) {
    var old = db.queryForList("select * from " + table + " where id=?", event);
    if (old.isEmpty()) return false;
    var row = new LinkedHashMap<String, Object>();
    old.getFirst().forEach((k, v) -> row.put(k.toLowerCase(Locale.ROOT), v));
    if (!target.equals(row.get(targetKey))
        || !action.equals(row.get("action"))
        || !reason.equals(row.get("reason"))
        || !actor().equals(row.get("actor")))
      throw conflict("Review event ID used with different data or actor");
    return true;
  }

  Map<String, Object> reverse(UUID adjustment, CorrectionController.ReversalInput r) {
    String payload =
        json.writeValueAsString(
            List.of(adjustment, r.reason().strip(), r.evidenceReference().strip()));
    var replay = replay("stock_reversals", r.reversalId(), payload);
    if (replay != null) return replay;
    var adj = one("stock_adjustments", adjustment, true);
    if (!"APPROVED".equals(adj.get("status")))
      throw conflict("Only an approved adjustment or return can be reversed");
    date(((java.sql.Date) adj.get("occurred_on")).toLocalDate());
    if (db.queryForObject(
            "select count(*) from stock_reversal_claims where adjustment_id=?",
            Integer.class,
            adjustment)
        > 0) throw conflict("A reversal is already pending or approved for this record");
    db.update(
        "insert into"
            + " stock_reversals(id,adjustment_id,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?)",
        r.reversalId(),
        adjustment,
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        payload);
    db.update(
        "insert into stock_reversal_claims(adjustment_id,reversal_id) values(?,?)",
        adjustment,
        r.reversalId());
    audit.record("STOCK_REVERSAL_SUBMITTED", "STOCK_REVERSAL", r.reversalId(), "Source=" + adjustment);
    return get("stock_reversals", r.reversalId(), "reversal_id", "stock_reversal_events");
  }

  Map<String, Object> decideReversal(UUID id, CorrectionController.Decision r, boolean approve) {
    var record = one("stock_reversals", id, true);
    String action = approve ? "APPROVE" : "REJECT";
    if (replayEvent("stock_reversal_events", r.eventId(), id, "reversal_id", action, r.reason())) {
      var result = get("stock_reversals", id, "reversal_id", "stock_reversal_events");
      result.put("replayed", true);
      return result;
    }
    if (!"PENDING".equals(record.get("status"))) throw conflict("Reversal already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the reversal");
    var adj = one("stock_adjustments", (UUID) record.get("adjustment_id"), true);
    if (approve) {
      var sourceYard = yard((UUID) adj.get("yard_id"), true);
      materials.getActive((UUID) adj.get("material_id"));
      boolean usable =
          !"CUSTOMER_RETURN".equals(adj.get("kind"))
              || Boolean.TRUE.equals(adj.get("resalable"))
              || "QUARANTINE".equals(sourceYard.get("kind"));
      if (usable) {
        BigDecimal qty = (BigDecimal) adj.get("quantity");
        BigDecimal delta = "DECREASE".equals(adj.get("kind")) ? qty : qty.negate();
        apply((UUID) adj.get("yard_id"), (UUID) adj.get("material_id"), delta);
        move(
            r.eventId(),
            "REVERSAL",
            id,
            (UUID) adj.get("yard_id"),
            (UUID) adj.get("material_id"),
            delta.signum() < 0 ? "ADJUST_OUT" : "ADJUST_IN",
            qty,
            "Reversal of " + adj.get("id"));
      }
    }
    finish(
        "stock_reversals",
        "stock_reversal_events",
        "reversal_id",
        id,
        r,
        approve,
        "STOCK_REVERSAL");
    if (!approve) db.update("delete from stock_reversal_claims where reversal_id=?", id);
    return get("stock_reversals", id, "reversal_id", "stock_reversal_events");
  }

  Map<String, Object> opening(CorrectionController.OpeningInput r) {
    String payload =
        json.writeValueAsString(
            List.of(
                r.yardId(),
                r.materialId(),
                number(r.quantity()),
                r.occurredOn(),
                r.reason().strip(),
                r.evidenceReference().strip()));
    var replay = replay("stock_openings", r.openingId(), payload);
    if (replay != null) return replay;
    yard(r.yardId(), true);
    materials.getActive(r.materialId());
    date(r.occurredOn());
    if (db.queryForObject(
            "select count(*) from stock_opening_claims where yard_id=? and material_id=?",
            Integer.class,
            r.yardId(),
            r.materialId())
        > 0) throw conflict("An opening balance already exists for this yard and material");
    var s = stock(r.yardId(), r.materialId());
    if (((BigDecimal) s.get("on_hand")).signum() != 0)
      throw conflict("Opening stock requires a zero on-hand balance; use a count adjustment");
    db.update(
        "insert into"
            + " stock_openings(id,yard_id,material_id,quantity,occurred_on,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?)",
        r.openingId(),
        r.yardId(),
        r.materialId(),
        r.quantity(),
        r.occurredOn(),
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        payload);
    db.update(
        "insert into stock_opening_claims(yard_id,material_id,opening_id) values(?,?,?)",
        r.yardId(),
        r.materialId(),
        r.openingId());
    audit.record("STOCK_OPENING_SUBMITTED", "STOCK_OPENING", r.openingId(), "Yard=" + r.yardId());
    return get("stock_openings", r.openingId(), "opening_id", "stock_opening_events");
  }

  Map<String, Object> decideOpening(UUID id, CorrectionController.Decision r, boolean approve) {
    var record = one("stock_openings", id, true);
    String action = approve ? "APPROVE" : "REJECT";
    if (replayEvent("stock_opening_events", r.eventId(), id, "opening_id", action, r.reason())) {
      var result = get("stock_openings", id, "opening_id", "stock_opening_events");
      result.put("replayed", true);
      return result;
    }
    if (!"PENDING".equals(record.get("status"))) throw conflict("Opening already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the opening");
    if (approve) {
      yard((UUID) record.get("yard_id"), true);
      materials.getActive((UUID) record.get("material_id"));
      var s = stock((UUID) record.get("yard_id"), (UUID) record.get("material_id"));
      if (((BigDecimal) s.get("on_hand")).signum() != 0)
        throw conflict("Stock changed before opening approval; reject and count instead");
      apply((UUID) record.get("yard_id"), (UUID) record.get("material_id"), (BigDecimal) record.get("quantity"));
      move(
          r.eventId(),
          "OPENING",
          id,
          (UUID) record.get("yard_id"),
          (UUID) record.get("material_id"),
          "OPENING",
          (BigDecimal) record.get("quantity"),
          "Opening stock " + id);
    }
    finish("stock_openings", "stock_opening_events", "opening_id", id, r, approve, "STOCK_OPENING");
    if (!approve) db.update("delete from stock_opening_claims where opening_id=?", id);
    return get("stock_openings", id, "opening_id", "stock_opening_events");
  }

  Map<String, Object> transfer(CorrectionController.TransferInput r) {
    String payload =
        json.writeValueAsString(
            List.of(
                r.fromYardId(),
                r.toYardId(),
                r.materialId(),
                number(r.quantity()),
                r.occurredOn(),
                r.reason().strip(),
                r.evidenceReference().strip()));
    var replay = replay("stock_transfers", r.transferId(), payload);
    if (replay != null) return replay;
    date(r.occurredOn());
    materials.getActive(r.materialId());
    UUID first = r.fromYardId().compareTo(r.toYardId()) < 0 ? r.fromYardId() : r.toYardId();
    UUID second = first.equals(r.fromYardId()) ? r.toYardId() : r.fromYardId();
    var a = yard(first, true);
    var b = yard(second, true);
    var fromYard = first.equals(r.fromYardId()) ? a : b;
    var toYard = first.equals(r.fromYardId()) ? b : a;
    boolean sameBranch = fromYard.get("branch_id").equals(toYard.get("branch_id"));
    if (!sameBranch
        && (!"OPERATING".equals(fromYard.get("kind")) || !"OPERATING".equals(toYard.get("kind"))))
      throw conflict("Cross-branch transfers are only allowed between operating yards");
    var from = stock(r.fromYardId(), r.materialId());
    if (((BigDecimal) from.get("on_hand"))
            .subtract((BigDecimal) from.get("reserved"))
            .compareTo(r.quantity())
        < 0) throw conflict("Source yard does not have enough available stock");
    db.update(
        "insert into"
            + " stock_transfers(id,from_yard_id,to_yard_id,material_id,quantity,occurred_on,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?)",
        r.transferId(),
        r.fromYardId(),
        r.toYardId(),
        r.materialId(),
        r.quantity(),
        r.occurredOn(),
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        payload);
    audit.record("STOCK_TRANSFER_SUBMITTED", "STOCK_TRANSFER", r.transferId(), "Material=" + r.materialId());
    return get("stock_transfers", r.transferId(), "transfer_id", "stock_transfer_events");
  }

  Map<String, Object> decideTransfer(UUID id, CorrectionController.Decision r, boolean approve) {
    var record = one("stock_transfers", id, true);
    String action = approve ? "APPROVE" : "REJECT";
    if (replayEvent("stock_transfer_events", r.eventId(), id, "transfer_id", action, r.reason())) {
      var result = get("stock_transfers", id, "transfer_id", "stock_transfer_events");
      result.put("replayed", true);
      return result;
    }
    if (!"PENDING".equals(record.get("status"))) throw conflict("Transfer already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the transfer");
    if (approve) {
      UUID from = (UUID) record.get("from_yard_id"), to = (UUID) record.get("to_yard_id");
      UUID first = from.compareTo(to) < 0 ? from : to;
      UUID second = first.equals(from) ? to : from;
      yard(first, true);
      yard(second, true);
      materials.getActive((UUID) record.get("material_id"));
      BigDecimal qty = (BigDecimal) record.get("quantity");
      UUID material = (UUID) record.get("material_id");
      apply(from, material, qty.negate());
      ensureStock(to, material);
      apply(to, material, qty);
      move(r.eventId(), "TRANSFER", id, from, (UUID) record.get("material_id"), "TRANSFER_OUT", qty, "Transfer " + id);
      move(
          UUID.nameUUIDFromBytes((r.eventId() + ":in").getBytes()),
          "TRANSFER",
          id,
          to,
          (UUID) record.get("material_id"),
          "TRANSFER_IN",
          qty,
          "Transfer " + id);
    }
    finish("stock_transfers", "stock_transfer_events", "transfer_id", id, r, approve, "STOCK_TRANSFER");
    return get("stock_transfers", id, "transfer_id", "stock_transfer_events");
  }

  Map<String, Object> supplierReturn(UUID purchase, CorrectionController.SupplierReturnInput r) {
    String payload =
        json.writeValueAsString(
            List.of(
                purchase,
                number(r.quantity()),
                r.occurredOn(),
                r.reason().strip(),
                r.evidenceReference().strip()));
    var replay = replay("supplier_returns", r.returnId(), payload);
    if (replay != null) return replay;
    var p = one("purchase_orders", purchase, true);
    if (!"YARD".equals(p.get("destination")) || p.get("yard_id") == null)
      throw conflict("Supplier returns require a yard purchase");
    yard((UUID) p.get("yard_id"), true);
    date(r.occurredOn());
    BigDecimal used =
        db.queryForObject(
            "select coalesce(sum(quantity),0) from supplier_returns where purchase_id=? and status"
                + " in ('PENDING','APPROVED')",
            BigDecimal.class,
            purchase);
    if (used.add(r.quantity()).compareTo((BigDecimal) p.get("accepted_quantity")) > 0)
      throw conflict("Supplier return exceeds accepted yard receipts on this purchase");
    var s = stock((UUID) p.get("yard_id"), (UUID) p.get("material_id"));
    if (((BigDecimal) s.get("on_hand"))
            .subtract((BigDecimal) s.get("reserved"))
            .compareTo(r.quantity())
        < 0) throw conflict("Yard does not have enough available stock for the supplier return");
    db.update(
        "insert into"
            + " supplier_returns(id,purchase_id,yard_id,material_id,quantity,occurred_on,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?)",
        r.returnId(),
        purchase,
        p.get("yard_id"),
        p.get("material_id"),
        r.quantity(),
        r.occurredOn(),
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        payload);
    audit.record("SUPPLIER_RETURN_SUBMITTED", "SUPPLIER_RETURN", r.returnId(), "Purchase=" + purchase);
    return get("supplier_returns", r.returnId(), "return_id", "supplier_return_events");
  }

  Map<String, Object> decideSupplier(UUID id, CorrectionController.Decision r, boolean approve) {
    var record = one("supplier_returns", id, true);
    String action = approve ? "APPROVE" : "REJECT";
    if (replayEvent("supplier_return_events", r.eventId(), id, "return_id", action, r.reason())) {
      var result = get("supplier_returns", id, "return_id", "supplier_return_events");
      result.put("replayed", true);
      return result;
    }
    if (!"PENDING".equals(record.get("status"))) throw conflict("Supplier return already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the supplier return");
    if (approve) {
      yard((UUID) record.get("yard_id"), true);
      apply(
          (UUID) record.get("yard_id"),
          (UUID) record.get("material_id"),
          ((BigDecimal) record.get("quantity")).negate());
      move(
          r.eventId(),
          "SUPPLIER_RETURN",
          id,
          (UUID) record.get("yard_id"),
          (UUID) record.get("material_id"),
          "SUPPLIER_RETURN",
          (BigDecimal) record.get("quantity"),
          "Supplier return " + id);
    }
    finish("supplier_returns", "supplier_return_events", "return_id", id, r, approve, "SUPPLIER_RETURN");
    return get("supplier_returns", id, "return_id", "supplier_return_events");
  }

  Map<String, Object> receiptReversal(UUID receipt, CorrectionController.ReversalInput r) {
    String payload =
        json.writeValueAsString(
            List.of(receipt, r.reason().strip(), r.evidenceReference().strip()));
    var replay = replay("purchase_receipt_reversals", r.reversalId(), payload);
    if (replay != null) return replay;
    var rec = one("purchase_receipts", receipt, true);
    var p = one("purchase_orders", (UUID) rec.get("purchase_id"), true);
    if (!Set.of("OPEN", "RECEIVED").contains((String) p.get("status")))
      throw conflict("Closed purchases cannot reverse receipts");
    if (db.queryForObject(
            "select count(*) from trip_supply_claims where trip_id=?", Integer.class, receipt)
        > 0)
      throw conflict("Trip delivery receipts are reversed through a trip weight correction");
    if (db.queryForObject(
            "select count(*) from billing_source_claims where kind='PURCHASE_RECEIPT' and"
                + " source_id=?",
            Integer.class,
            receipt)
        > 0)
      throw conflict("Cancel the billing preparation before reversing this receipt");
    if (db.queryForObject(
            "select count(*) from purchase_receipt_reversal_claims where receipt_id=?",
            Integer.class,
            receipt)
        > 0) throw conflict("A reversal is already pending or approved for this receipt");
    db.update(
        "insert into"
            + " purchase_receipt_reversals(id,receipt_id,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?)",
        r.reversalId(),
        receipt,
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        payload);
    db.update(
        "insert into purchase_receipt_reversal_claims(receipt_id,reversal_id) values(?,?)",
        receipt,
        r.reversalId());
    audit.record(
        "PURCHASE_RECEIPT_REVERSAL_SUBMITTED",
        "PURCHASE_RECEIPT_REVERSAL",
        r.reversalId(),
        "Receipt=" + receipt);
    return get(
        "purchase_receipt_reversals",
        r.reversalId(),
        "reversal_id",
        "purchase_receipt_reversal_events");
  }

  Map<String, Object> decideReceipt(UUID id, CorrectionController.Decision r, boolean approve) {
    var record = one("purchase_receipt_reversals", id, true);
    String action = approve ? "APPROVE" : "REJECT";
    if (replayEvent(
        "purchase_receipt_reversal_events", r.eventId(), id, "reversal_id", action, r.reason())) {
      var result =
          get("purchase_receipt_reversals", id, "reversal_id", "purchase_receipt_reversal_events");
      result.put("replayed", true);
      return result;
    }
    if (!"PENDING".equals(record.get("status"))) throw conflict("Receipt reversal already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the receipt reversal");
    if (approve) {
      var rec = one("purchase_receipts", (UUID) record.get("receipt_id"), true);
      var p = one("purchase_orders", (UUID) rec.get("purchase_id"), true);
      if (!Set.of("OPEN", "RECEIVED").contains((String) p.get("status")))
        throw conflict("Closed purchases cannot reverse receipts");
      BigDecimal used =
          db.queryForObject(
              "select coalesce(sum(quantity),0) from supplier_returns where purchase_id=? and"
                  + " status in ('PENDING','APPROVED')",
              BigDecimal.class,
              p.get("id"));
      BigDecimal nextAccepted =
          ((BigDecimal) p.get("accepted_quantity")).subtract((BigDecimal) rec.get("accepted_quantity"));
      if (used.compareTo(nextAccepted) > 0)
        throw conflict("Supplier returns already consume this receipt's accepted quantity");
      BigDecimal nextReceived =
          ((BigDecimal) p.get("received_quantity")).subtract((BigDecimal) rec.get("received_quantity"));
      UUID yard = (UUID) p.get("yard_id");
      if (yard != null && ((BigDecimal) rec.get("accepted_quantity")).signum() > 0) {
        yard(yard, true);
        apply(yard, (UUID) p.get("material_id"), ((BigDecimal) rec.get("accepted_quantity")).negate());
        db.update(
            "insert into"
                + " execution_stock_movements(id,source_type,source_id,yard_id,material_id,kind,quantity,reference,recorded_by)"
                + " values(?,?,?,?,?,'ADJUST_OUT',?,?,?)",
            r.eventId(),
            "RECEIPT_REVERSAL",
            id,
            yard,
            p.get("material_id"),
            rec.get("accepted_quantity"),
            "Receipt reversal " + id,
            actor());
      }
      db.update(
          "update purchase_orders set received_quantity=?,accepted_quantity=?,status=? where id=?",
          nextReceived,
          nextAccepted,
          nextReceived.signum() == 0
              ? "OPEN"
              : nextReceived.compareTo((BigDecimal) p.get("quantity")) == 0 ? "RECEIVED" : "OPEN",
          p.get("id"));
    }
    finish(
        "purchase_receipt_reversals",
        "purchase_receipt_reversal_events",
        "reversal_id",
        id,
        r,
        approve,
        "PURCHASE_RECEIPT_REVERSAL");
    if (!approve) db.update("delete from purchase_receipt_reversal_claims where reversal_id=?", id);
    return get("purchase_receipt_reversals", id, "reversal_id", "purchase_receipt_reversal_events");
  }

  private void finish(
      String table,
      String events,
      String key,
      UUID id,
      CorrectionController.Decision r,
      boolean approve,
      String auditType) {
    db.update(
        "insert into " + events + "(id," + key + ",action,reason,actor) values(?,?,?,?,?)",
        r.eventId(),
        id,
        approve ? "APPROVE" : "REJECT",
        r.reason(),
        actor());
    db.update(
        "update "
            + table
            + " set status=?,reviewed_by=?,reviewed_at=?,review_reason=? where id=?",
        approve ? "APPROVED" : "REJECTED",
        actor(),
        java.sql.Timestamp.from(clock.instant()),
        r.reason(),
        id);
    audit.record(
        auditType + (approve ? "_APPROVED" : "_REJECTED"), auditType, id, "Event=" + r.eventId());
  }
}
