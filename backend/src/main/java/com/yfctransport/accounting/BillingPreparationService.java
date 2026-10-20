package com.yfctransport.accounting;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import com.yfctransport.inventory.PurchaseBilling;
import com.yfctransport.orders.OrderBilling;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
class BillingPreparationService {
  private final JdbcTemplate db;
  private final OrderBilling orders;
  private final PurchaseBilling purchases;
  private final BranchRegistry branches;
  private final AccountingService accounting;
  private final AuditTrail audit;
  private final Clock clock;
  private final ObjectMapper json;

  BillingPreparationService(
      JdbcTemplate db,
      OrderBilling orders,
      PurchaseBilling purchases,
      BranchRegistry branches,
      AccountingService accounting,
      AuditTrail audit,
      Clock clock,
      ObjectMapper json) {
    this.db = db;
    this.orders = orders;
    this.purchases = purchases;
    this.branches = branches;
    this.accounting = accounting;
    this.audit = audit;
    this.clock = clock;
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

  private Map<String, Object> one(UUID id) {
    var rows = db.queryForList("select * from billing_preparations where id=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Billing preparation not found");
    return norm(rows.getFirst());
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = norm(row);
    result.remove("create_payload");
    result.remove("source_snapshot");
    result.put("currency", "INR");
    result.put("unit", "TONNE");
    result.put("tax_treatment", "PENDING_ACCOUNTANT_CONFIGURATION");
    result.put("document_type", "BILLING_PREPARATION");
    result.put("invoice_issued", false);
    UUID journal = (UUID) result.get("journal_id");
    String journalStatus = null;
    if (journal != null) {
      journalStatus =
          db.queryForObject("select status from journals where id=?", String.class, journal);
      result.put("journal_status", journalStatus);
    }
    result.put("ledger_posted", "POSTED".equals(journalStatus));
    return result;
  }

  private record Line(
      UUID id,
      String material,
      BigDecimal quantity,
      BigDecimal loaded,
      BigDecimal received,
      BigDecimal accepted,
      BigDecimal materialRate,
      BigDecimal transportRate) {
    BigDecimal materialAmount() {
      return quantity.multiply(materialRate).setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal transportAmount() {
      return quantity.multiply(transportRate).setScale(2, RoundingMode.HALF_UP);
    }
  }

  Map<String, Object> create(BillingPreparationController.Input r) {
    lock();
    String payload =
        json.writeValueAsString(
            List.of(
                r.kind(),
                r.sourceId(),
                r.preparedOn(),
                r.quantityBasis(),
                r.reference().strip(),
                r.evidenceReference().strip(),
                r.basisReason().strip(),
                actor()));
    if (db.queryForObject(
            "select count(*) from billing_preparations where id=?",
            Integer.class,
            r.preparationId())
        > 0) {
      if (!payload.equals(one(r.preparationId()).get("create_payload")))
        throw conflict("Preparation ID used with different data or actor");
      var result = get(r.preparationId());
      result.put("replayed", true);
      return result;
    }
    if (r.preparedOn().isAfter(LocalDate.now(clock)))
      throw new IllegalArgumentException("Preparation date cannot be in the future");
    if (db.queryForObject(
            "select count(*) from billing_source_claims where kind=? and source_id=?",
            Integer.class,
            r.kind().name(),
            r.sourceId())
        > 0)
      throw conflict(
          "Source already has an active billing preparation; cancel or reject it before replacing");
    UUID party, branch;
    String name, snapshot;
    LocalDate sourceDate;
    List<Line> lines;
    if (r.kind() == BillingPreparationController.Kind.SALES_ORDER) {
      if (r.quantityBasis() != BillingPreparationController.Basis.ORDERED)
        throw new IllegalArgumentException("Sales billing must use the agreed ORDERED quantity");
      var s = orders.snapshot(r.sourceId());
      party = s.customerId();
      branch = s.branchId();
      name = s.customerName();
      sourceDate = s.createdOn();
      snapshot = json.writeValueAsString(s);
      lines =
          s.lines().stream()
              .map(
                  l ->
                      new Line(
                          l.itemId(),
                          l.material(),
                          l.orderedQuantity(),
                          l.loadedQuantity(),
                          l.receivedQuantity(),
                          null,
                          l.materialRate(),
                          l.transportRate()))
              .toList();
    } else {
      if (r.quantityBasis() == BillingPreparationController.Basis.ORDERED)
        throw new IllegalArgumentException(
            "Choose LOADED, RECEIVED or ACCEPTED for a supplier receipt");
      var s = purchases.receipt(r.sourceId());
      party = s.supplierId();
      branch = s.branchId();
      name = s.supplierName();
      sourceDate = s.receivedOn();
      snapshot = json.writeValueAsString(s);
      BigDecimal quantity =
          switch (r.quantityBasis()) {
            case LOADED -> s.loadedQuantity();
            case RECEIVED -> s.receivedQuantity();
            case ACCEPTED -> s.acceptedQuantity();
            default -> throw new IllegalArgumentException("Unsupported receipt basis");
          };
      lines =
          List.of(
              new Line(
                  s.receiptId(),
                  s.material(),
                  quantity,
                  s.loadedQuantity(),
                  s.receivedQuantity(),
                  s.acceptedQuantity(),
                  s.rate(),
                  BigDecimal.ZERO));
    }
    if (branch == null) throw conflict("Source must have a branch before billing preparation");
    branches.requireActive(branch);
    if (r.preparedOn().isBefore(sourceDate))
      throw new IllegalArgumentException(
          "Preparation date cannot precede its source order or receipt");
    if (lines.isEmpty() || lines.stream().anyMatch(l -> l.quantity().signum() <= 0))
      throw conflict("Billing preparation requires positive source quantities");
    BigDecimal subtotal =
        lines.stream()
            .map(l -> l.materialAmount().add(l.transportAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    db.update(
        "insert into"
            + " billing_preparations(id,kind,source_id,party_id,branch_id,party_name,prepared_on,quantity_basis,reference,evidence_reference,basis_reason,source_snapshot,subtotal,created_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        r.preparationId(),
        r.kind().name(),
        r.sourceId(),
        party,
        branch,
        name,
        r.preparedOn(),
        r.quantityBasis().name(),
        r.reference().strip(),
        r.evidenceReference().strip(),
        r.basisReason().strip(),
        snapshot,
        subtotal,
        actor(),
        payload);
    int position = 0;
    for (var l : lines)
      db.update(
          "insert into"
              + " billing_preparation_lines(preparation_id,position,source_line_id,material,quantity,loaded_quantity,received_quantity,accepted_quantity,material_rate,transport_rate,material_amount,transport_amount)"
              + " values(?,?,?,?,?,?,?,?,?,?,?,?)",
          r.preparationId(),
          ++position,
          l.id(),
          l.material(),
          l.quantity(),
          l.loaded(),
          l.received(),
          l.accepted(),
          l.materialRate(),
          l.transportRate(),
          l.materialAmount(),
          l.transportAmount());
    db.update(
        "insert into billing_source_claims(kind,source_id,preparation_id) values(?,?,?)",
        r.kind().name(),
        r.sourceId(),
        r.preparationId());
    audit.record(
        "BILLING_PREPARATION_CREATED",
        "BILLING_PREPARATION",
        r.preparationId(),
        "Source=" + r.kind() + ":" + r.sourceId());
    return get(r.preparationId());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Map<String, Object> get(UUID id) {
    var row = one(id);
    var result = view(row);
    result.put(
        "source_snapshot",
        json.readerFor(Map.class)
            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .readValue((String) row.get("source_snapshot")));
    result.put(
        "lines",
        db
            .queryForList(
                "select"
                    + " position,source_line_id,material,quantity,loaded_quantity,received_quantity,accepted_quantity,material_rate,transport_rate,material_amount,transport_amount"
                    + " from billing_preparation_lines where preparation_id=? order by position",
                id)
            .stream()
            .map(this::norm)
            .toList());
    result.put(
        "events",
        db
            .queryForList(
                "select id,action,reason,actor,occurred_at from billing_preparation_events where"
                    + " preparation_id=? order by occurred_at,id",
                id)
            .stream()
            .map(this::norm)
            .toList());
    return result;
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(
      int page, int size, BillingPreparationController.Kind kind, UUID party, UUID branch) {
    var args = new ArrayList<Object>();
    String where = " where 1=1";
    if (kind != null) {
      where += " and kind=?";
      args.add(kind.name());
    }
    if (party != null) {
      where += " and party_id=?";
      args.add(party);
    }
    if (branch != null) {
      where += " and branch_id=?";
      args.add(branch);
    }
    args.add(size);
    args.add((long) page * size);
    return db
        .queryForList(
            "select"
                + " id,kind,source_id,party_id,branch_id,party_name,prepared_on,quantity_basis,reference,evidence_reference,basis_reason,subtotal,status,journal_id,created_by,created_at"
                + " from billing_preparations"
                + where
                + " order by created_at,id limit ? offset ?",
            args.toArray())
        .stream()
        .map(this::view)
        .toList();
  }

  Map<String, Object> decide(UUID id, AccountingDtos.Decision r, String action) {
    lock();
    var row = one(id);
    var old = db.queryForList("select * from billing_preparation_events where id=?", r.eventId());
    String reason = r.reason().strip();
    if (!old.isEmpty()) {
      var event = norm(old.getFirst());
      if (!id.equals(event.get("preparation_id"))
          || !action.equals(event.get("action"))
          || !reason.equals(event.get("reason"))
          || !actor().equals(event.get("actor")))
        throw conflict("Event ID used with different data or actor");
      var result = get(id);
      result.put("replayed", true);
      return result;
    }
    String status = (String) row.get("status");
    if (!(status.equals("DRAFT") || (status.equals("READY") && action.equals("CANCEL"))))
      throw conflict(
          "Only draft preparations can be reviewed; ready preparations may be cancelled");
    if (actor().equals(row.get("created_by")))
      throw conflict("A different user must review or cancel the preparation");
    if (action.equals("CANCEL") && row.get("journal_id") != null) {
      String journalStatus =
          db.queryForObject(
              "select status from journals where id=?", String.class, row.get("journal_id"));
      if ("DRAFT".equals(journalStatus) || "POSTED".equals(journalStatus))
        throw conflict("Reject or reverse the linked journal before cancelling this preparation");
    }
    if (action.equals("READY")) branches.requireActive((UUID) row.get("branch_id"));
    db.update(
        "insert into billing_preparation_events(id,preparation_id,action,reason,actor)"
            + " values(?,?,?,?,?)",
        r.eventId(),
        id,
        action,
        reason,
        actor());
    db.update(
        "update billing_preparations set status=? where id=?",
        switch (action) {
          case "READY" -> "READY";
          case "REJECT" -> "REJECTED";
          case "CANCEL" -> "CANCELLED";
          default -> throw new IllegalArgumentException("Unsupported billing review");
        },
        id);
    if (!action.equals("READY"))
      db.update("delete from billing_source_claims where preparation_id=?", id);
    audit.record(
        "BILLING_PREPARATION_" + action, "BILLING_PREPARATION", id, "Event=" + r.eventId());
    return get(id);
  }

  Map<String, Object> journal(UUID id, BillingPreparationController.JournalInput r) {
    lock();
    var row = one(id);
    String payload =
        json.writeValueAsString(
            List.of(
                id,
                r.periodId(),
                r.entryDate(),
                r.counterAccountId(),
                r.description().strip(),
                r.reference().strip(),
                actor()));
    if (db.queryForObject(
            "select count(*) from billing_preparation_journals where journal_id=?",
            Integer.class,
            r.journalId())
        > 0) {
      var old =
          norm(
              db.queryForList(
                      "select * from billing_preparation_journals where journal_id=?",
                      r.journalId())
                  .getFirst());
      if (!payload.equals(old.get("create_payload")) || !id.equals(old.get("preparation_id")))
        throw conflict("Journal ID used with different billing data or actor");
      var result = get(id);
      result.put("replayed", true);
      return result;
    }
    if (db.queryForObject("select count(*) from journals where id=?", Integer.class, r.journalId())
        > 0) throw conflict("Journal ID already used");
    if (!"READY".equals(row.get("status")))
      throw conflict("Only a READY billing preparation can be posted to the ledger");
    UUID current = (UUID) row.get("journal_id");
    if (current != null) {
      String status = db.queryForObject("select status from journals where id=?", String.class, current);
      if (!"REJECTED".equals(status))
        throw conflict("Preparation already has a draft or posted journal");
    }
    if (((BigDecimal) row.get("subtotal")).signum() <= 0)
      throw conflict("Zero-priced preparations cannot create a ledger journal");
    branches.requireActive((UUID) row.get("branch_id"));
    String partyType =
        BillingPreparationController.Kind.SALES_ORDER.name().equals(row.get("kind"))
            ? "CUSTOMER"
            : "SUPPLIER";
    var profiles =
        db.queryForList(
            "select * from party_financial_profiles where party_type=? and "
                + (partyType.equals("CUSTOMER") ? "customer_id" : "supplier_id")
                + "=?",
            partyType,
            row.get("party_id"));
    if (profiles.isEmpty())
      throw conflict("Create a party financial profile before posting this preparation");
    UUID partyAccount = (UUID) norm(profiles.getFirst()).get("account_id");
    var counter = norm(db.queryForList("select * from ledger_accounts where id=?", r.counterAccountId()).stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Counter account not found")));
    if (!Boolean.TRUE.equals(counter.get("active"))) throw conflict("Counter account is inactive");
    String expected = partyType.equals("CUSTOMER") ? "INCOME" : "EXPENSE";
    if (!expected.equals(counter.get("type")))
      throw conflict(
          partyType.equals("CUSTOMER")
              ? "Sales posting requires an active INCOME counter account"
              : "Supplier posting requires an active EXPENSE counter account");
    if (r.counterAccountId().equals(partyAccount)
        || db.queryForObject(
                "select count(*) from treasury_accounts where account_id=?",
                Integer.class,
                r.counterAccountId())
            > 0
        || db.queryForObject(
                "select count(*) from party_financial_profiles where account_id=?",
                Integer.class,
                r.counterAccountId())
            > 0)
      throw conflict("Counter account cannot be a party or cash/bank account");
    if (r.entryDate().isBefore((LocalDate) day(row.get("prepared_on"))))
      throw new IllegalArgumentException("Journal date cannot precede the preparation date");
    BigDecimal amount = (BigDecimal) row.get("subtotal");
    boolean sales = partyType.equals("CUSTOMER");
    var partyLine =
        new AccountingDtos.Line(
            partyAccount,
            sales ? amount : BigDecimal.ZERO,
            sales ? BigDecimal.ZERO : amount,
            "Party " + partyType.toLowerCase(Locale.ROOT) + " " + row.get("party_name"));
    var counterLine =
        new AccountingDtos.Line(
            r.counterAccountId(),
            sales ? BigDecimal.ZERO : amount,
            sales ? amount : BigDecimal.ZERO,
            r.description().strip());
    accounting.journal(
        new AccountingDtos.Journal(
            r.journalId(),
            r.periodId(),
            (UUID) row.get("branch_id"),
            r.entryDate(),
            r.description().strip(),
            r.reference().strip(),
            List.of(partyLine, counterLine)));
    db.update(
        "insert into"
            + " billing_preparation_journals(journal_id,preparation_id,counter_account_id,create_payload,created_by)"
            + " values(?,?,?,?,?)",
        r.journalId(),
        id,
        r.counterAccountId(),
        payload,
        actor());
    db.update("update billing_preparations set journal_id=? where id=?", r.journalId(), id);
    audit.record(
        "BILLING_PREPARATION_JOURNAL", "BILLING_PREPARATION", id, "Journal=" + r.journalId());
    return get(id);
  }

  private LocalDate day(Object v) {
    if (v instanceof LocalDate d) return d;
    if (v instanceof java.sql.Date d) return d.toLocalDate();
    if (v instanceof java.sql.Timestamp t)
      return t.toInstant().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
    return ((java.util.Date) v).toInstant().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
  }
}
