package com.yfctransport.accounting;

import com.yfctransport.audit.AuditTrail;
import java.math.*;
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
class AllocationService {
  private final JdbcTemplate db;
  private final AuditTrail audit;
  private final ObjectMapper json;

  AllocationService(JdbcTemplate db, AuditTrail audit, ObjectMapper json) {
    this.db = db;
    this.audit = audit;
    this.json = json;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private void lock() {
    db.queryForObject("select id from accounting_guard where id=1 for update", Integer.class);
  }

  private ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }

  private Map<String, Object> norm(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = norm(row);
    result.remove("create_payload");
    result.put("currency", "INR");
    return result;
  }

  private Map<String, Object> one(UUID id) {
    var rows = db.queryForList("select * from invoice_allocations where id=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice allocation not found");
    return norm(rows.getFirst());
  }

  private boolean isPostedAndActive(UUID journalId) {
    if (journalId == null) return false;
    var rows = db.queryForList("select status from journals where id=?", journalId);
    if (rows.isEmpty() || !"POSTED".equals(norm(rows.getFirst()).get("status"))) return false;
    return db.queryForObject(
            "select count(*) from journals where reverses_id=? and status='POSTED'",
            Integer.class,
            journalId)
        == 0;
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  Map<String, Object> allocate(AllocationController.Input r) {
    lock();
    BigDecimal amount = money(r.amount());
    String note = r.note() == null || r.note().isBlank() ? null : r.note().strip();
    var payloadItems = new ArrayList<Object>();
    payloadItems.add(r.voucherId());
    payloadItems.add(r.preparationId());
    payloadItems.add(amount);
    payloadItems.add(note);
    payloadItems.add(actor());
    String payload = json.writeValueAsString(payloadItems);
    var existing = db.queryForList("select * from invoice_allocations where id=?", r.allocationId());
    if (!existing.isEmpty()) {
      var old = norm(existing.getFirst());
      if (!payload.equals(old.get("create_payload")))
        throw conflict("Allocation ID used with different data or actor");
      var result = view(old);
      result.put("replayed", true);
      return result;
    }

    var vouchers = db.queryForList("select * from money_vouchers where id=?", r.voucherId());
    if (vouchers.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Money voucher not found");
    var voucher = norm(vouchers.getFirst());
    String voucherKind = (String) voucher.get("kind");
    if (!"CUSTOMER_RECEIPT".equals(voucherKind) && !"SUPPLIER_PAYMENT".equals(voucherKind))
      throw conflict("Only customer receipts and supplier payments can be allocated to bills");
    if (!isPostedAndActive(r.voucherId()))
      throw conflict("Money voucher must be posted and not reversed before allocation");

    var preps = db.queryForList("select * from billing_preparations where id=?", r.preparationId());
    if (preps.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Billing preparation not found");
    var prep = norm(preps.getFirst());
    if (!"READY".equals(prep.get("status")))
      throw conflict("Only READY bills can be allocated");
    UUID billJournalId = (UUID) prep.get("journal_id");
    if (!isPostedAndActive(billJournalId))
      throw conflict("Bill must have a posted, non-reversed journal before allocation");

    var taxes =
        db.queryForList(
            "select payable_total from billing_tax_assessments where preparation_id=?",
            r.preparationId());
    if (taxes.isEmpty())
      throw conflict("Bill must have GST assessed before allocation");
    BigDecimal payableTotal = money((BigDecimal) norm(taxes.getFirst()).get("payable_total"));

    var profiles =
        db.queryForList(
            "select party_type,customer_id,supplier_id from party_financial_profiles where id=?",
            voucher.get("profile_id"));
    if (profiles.isEmpty()) throw conflict("Party financial profile not found");
    var profile = norm(profiles.getFirst());

    String prepKind = (String) prep.get("kind");
    UUID prepParty = (UUID) prep.get("party_id");
    if ("CUSTOMER_RECEIPT".equals(voucherKind)) {
      if (!"SALES_ORDER".equals(prepKind) || !prepParty.equals(profile.get("customer_id")))
        throw conflict("Customer receipt can only be allocated to the same customer's sales bill");
    } else {
      if (!"PURCHASE_RECEIPT".equals(prepKind) || !prepParty.equals(profile.get("supplier_id")))
        throw conflict("Supplier payment can only be allocated to the same supplier's purchase bill");
    }

    BigDecimal voucherAmount = money((BigDecimal) voucher.get("amount"));
    BigDecimal voucherUsed =
        money(
            db.queryForObject(
                "select coalesce(sum(amount),0) from invoice_allocations where voucher_id=? and"
                    + " status='ACTIVE'",
                BigDecimal.class,
                r.voucherId()));
    BigDecimal voucherRemaining = voucherAmount.subtract(voucherUsed);
    if (amount.compareTo(voucherRemaining) > 0)
      throw conflict(
          "Allocation exceeds unallocated voucher balance of " + voucherRemaining + " INR");

    BigDecimal billUsed =
        money(
            db.queryForObject(
                "select coalesce(sum(amount),0) from invoice_allocations where preparation_id=? and"
                    + " status='ACTIVE'",
                BigDecimal.class,
                r.preparationId()));
    BigDecimal billOutstanding = payableTotal.subtract(billUsed);
    if (amount.compareTo(billOutstanding) > 0)
      throw conflict(
          "Allocation exceeds outstanding bill balance of " + billOutstanding + " INR");

    db.update(
        "insert into invoice_allocations(id,voucher_id,preparation_id,amount,note,create_payload,created_by)"
            + " values(?,?,?,?,?,?,?)",
        r.allocationId(),
        r.voucherId(),
        r.preparationId(),
        amount,
        note,
        payload,
        actor());
    audit.record(
        "INVOICE_ALLOCATED",
        "INVOICE_ALLOCATION",
        r.allocationId(),
        "Voucher=" + r.voucherId() + "; bill=" + r.preparationId() + "; amount=" + amount);
    return get(r.allocationId());
  }

  Map<String, Object> reverse(UUID id, AccountingDtos.Decision r) {
    lock();
    var row = one(id);
    String reason = r.reason().strip();
    String currentActor = actor();
    if (row.get("reversal_event_id") != null) {
      if (!r.eventId().equals(row.get("reversal_event_id"))
          || !reason.equals(row.get("reversal_reason"))
          || !currentActor.equals(row.get("reversed_by")))
        throw conflict("Reversal event ID used with different data or actor");
      var result = view(row);
      result.put("replayed", true);
      return result;
    }
    if (db.queryForObject(
            "select count(*) from invoice_allocations where reversal_event_id=?",
            Integer.class,
            r.eventId())
        > 0) throw conflict("Reversal event ID already used on another allocation");
    if (!"ACTIVE".equals(row.get("status")))
      throw conflict("Only an ACTIVE allocation can be reversed");
    db.update(
        "update invoice_allocations set status='REVERSED',reversal_event_id=?,reversal_reason=?,"
            + " reversed_by=?,reversed_at=current_timestamp where id=?",
        r.eventId(),
        reason,
        currentActor,
        id);
    audit.record(
        "INVOICE_ALLOCATION_REVERSED",
        "INVOICE_ALLOCATION",
        id,
        "Event=" + r.eventId() + "; reason=" + reason);
    return get(id);
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(UUID id) {
    return view(one(id));
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(
      UUID voucherId, UUID preparationId, String status, int page, int size) {
    var where = new ArrayList<String>();
    var args = new ArrayList<Object>();
    if (voucherId != null) {
      where.add("a.voucher_id=?");
      args.add(voucherId);
    }
    if (preparationId != null) {
      where.add("a.preparation_id=?");
      args.add(preparationId);
    }
    if (status != null) {
      if (!List.of("ACTIVE", "REVERSED").contains(status))
        throw new IllegalArgumentException("Unknown allocation status");
      where.add("a.status=?");
      args.add(status);
    }
    String clause = where.isEmpty() ? "" : " where " + String.join(" and ", where);
    args.add(size);
    args.add((long) page * size);
    return db
        .queryForList(
            "select a.id,a.voucher_id,a.preparation_id,a.amount,a.note,a.status,a.created_by,"
                + " a.created_at,a.reversal_event_id,a.reversal_reason,a.reversed_by,a.reversed_at,"
                + " v.kind as voucher_kind,v.reference as voucher_reference,p.kind as bill_kind,"
                + " p.party_name,p.reference as bill_reference,t.invoice_number"
                + " from invoice_allocations a"
                + " join money_vouchers v on v.id=a.voucher_id"
                + " join billing_preparations p on p.id=a.preparation_id"
                + " left join billing_tax_assessments t on t.preparation_id=p.id"
                + clause
                + " order by a.created_at desc,a.id limit ? offset ?",
            args.toArray())
        .stream()
        .map(this::view)
        .toList();
  }
}
