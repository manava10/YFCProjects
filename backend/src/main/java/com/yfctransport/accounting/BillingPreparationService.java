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

  private boolean journalBlocksCancel(UUID journalId) {
    String status =
        db.queryForObject("select status from journals where id=?", String.class, journalId);
    if ("DRAFT".equals(status)) return true;
    if (!"POSTED".equals(status)) return false;
    return db.queryForObject(
            "select count(*) from journals where reverses_id=? and status='POSTED'",
            Integer.class,
            journalId)
        == 0;
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
    UUID journal = (UUID) result.get("journal_id");
    String journalStatus = null;
    if (journal != null) {
      journalStatus =
          db.queryForObject("select status from journals where id=?", String.class, journal);
      result.put("journal_status", journalStatus);
    }
    result.put("ledger_posted", "POSTED".equals(journalStatus));
    boolean blocked = journal != null && journalBlocksCancel(journal);
    result.put("gst_editable", "READY".equals(result.get("status")) && !blocked);
    var tax =
        db.queryForList(
            "select * from billing_tax_assessments where preparation_id=?", result.get("id"));
    if (tax.isEmpty()) {
      result.put("tax_treatment", "PENDING_ACCOUNTANT_CONFIGURATION");
      result.put("invoice_issued", false);
      result.put("document_type", "BILLING_PREPARATION");
    } else {
      var assessed = norm(tax.getFirst());
      boolean sales = "SALES_ORDER".equals(result.get("kind"));
      result.put("tax_treatment", "ASSESSED");
      result.put("supply", assessed.get("supply"));
      result.put("invoice_number", assessed.get("invoice_number"));
      result.put("invoice_issued", assessed.get("invoice_number") != null);
      result.put("taxable_amount", assessed.get("taxable_amount"));
      result.put("cgst_amount", assessed.get("cgst_amount"));
      result.put("sgst_amount", assessed.get("sgst_amount"));
      result.put("igst_amount", assessed.get("igst_amount"));
      result.put("tax_total", assessed.get("tax_total"));
      BigDecimal payable = ((BigDecimal) assessed.get("payable_total")).setScale(2, RoundingMode.HALF_UP);
      BigDecimal allocated =
          db.queryForObject(
                  "select coalesce(sum(amount),0) from invoice_allocations where preparation_id=?"
                      + " and status='ACTIVE'",
                  BigDecimal.class,
                  result.get("id"))
              .setScale(2, RoundingMode.HALF_UP);
      BigDecimal outstanding = payable.subtract(allocated);
      String paymentStatus =
          outstanding.signum() == 0
              ? "PAID"
              : allocated.signum() > 0 ? "PARTIALLY_PAID" : "UNPAID";
      result.put("payable_total", payable);
      result.put("allocated_amount", allocated);
      result.put("outstanding_amount", outstanding);
      result.put("payment_status", paymentStatus);
      result.put("document_type", sales ? "TAX_INVOICE" : "SUPPLIER_BILL");
    }
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
      if (r.quantityBasis() != BillingPreparationController.Basis.LOADED)
        throw new IllegalArgumentException("Supplier bills use the loaded quantity");
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
        "tax_lines",
        db
            .queryForList(
                "select"
                    + " position,component,hsn_or_sac,rate_percent,taxable_amount,cgst_amount,sgst_amount,igst_amount"
                    + " from billing_tax_lines where preparation_id=? order by position,component",
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
    var branchProfile = branches.profile((UUID) row.get("branch_id"));
    var branchMap = new LinkedHashMap<String, Object>();
    branchMap.put("id", branchProfile.id());
    branchMap.put("code", branchProfile.code());
    branchMap.put("name", branchProfile.name());
    branchMap.put("address", branchProfile.address());
    branchMap.put("city", branchProfile.city());
    branchMap.put("state", branchProfile.state());
    branchMap.put("pincode", branchProfile.pincode());
    branchMap.put("gstin", branchProfile.gstin());
    branchMap.put("state_code", branchProfile.stateCode());
    branchMap.put("bank_name", branchProfile.bankName());
    branchMap.put("bank_account", branchProfile.bankAccount());
    branchMap.put("bank_ifsc", branchProfile.bankIfsc());
    result.put("branch", branchMap);

    String partyTable = "SALES_ORDER".equals(row.get("kind")) ? "customers" : "suppliers";
    var partyRows =
        db.queryForList(
            "select gstin, address, mobile from " + partyTable + " where id=?", row.get("party_id"));
    String partyGstin = null;
    if (!partyRows.isEmpty()) {
      var p = norm(partyRows.getFirst());
      partyGstin = (String) p.get("gstin");
      result.put("party_gstin", partyGstin);
      result.put("party_address", p.get("address"));
      result.put("party_mobile", p.get("mobile"));
    }
    String partyStateCode =
        partyGstin != null && partyGstin.length() >= 2 ? partyGstin.substring(0, 2) : null;
    result.put("party_state_code", partyStateCode);
    if (branchProfile.stateCode() != null && partyStateCode != null) {
      result.put(
          "suggested_supply",
          branchProfile.stateCode().equals(partyStateCode) ? "INTRA_STATE" : "INTER_STATE");
    }
    return result;
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(
      int page,
      int size,
      BillingPreparationController.Kind kind,
      UUID party,
      UUID branch,
      String status,
      String q) {
    var args = new ArrayList<Object>();
    String where = " where 1=1";
    if (kind != null) {
      where += " and p.kind=?";
      args.add(kind.name());
    }
    if (party != null) {
      where += " and p.party_id=?";
      args.add(party);
    }
    if (branch != null) {
      where += " and p.branch_id=?";
      args.add(branch);
    }
    if (status != null && !status.isBlank()) {
      where += " and p.status=?";
      args.add(status.strip().toUpperCase(Locale.ROOT));
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
      where +=
          " and (lower(coalesce(p.party_name,'')) like ?"
              + " or lower(coalesce(p.reference,'')) like ?"
              + " or lower(coalesce(t.invoice_number,'')) like ?)";
      args.add(like);
      args.add(like);
      args.add(like);
    }
    args.add(size);
    args.add((long) page * size);
    return db
        .queryForList(
            "select p.id,p.kind,p.source_id,p.party_id,p.branch_id,p.party_name,p.prepared_on,"
                + " p.quantity_basis,p.reference,p.evidence_reference,p.basis_reason,p.subtotal,"
                + " p.status,p.journal_id,p.created_by,p.created_at,"
                + " j.status as j_status,"
                + " case when j.status='POSTED' and not exists(select 1 from journals r where"
                + " r.reverses_id=j.id and r.status='POSTED') then 1 else 0 end as j_active_posted,"
                + " t.supply as t_supply,t.invoice_number as t_invoice_number,"
                + " t.taxable_amount as t_taxable_amount,t.cgst_amount as t_cgst_amount,"
                + " t.sgst_amount as t_sgst_amount,t.igst_amount as t_igst_amount,"
                + " t.tax_total as t_tax_total,t.payable_total as t_payable_total,"
                + " coalesce((select sum(a.amount) from invoice_allocations a where"
                + " a.preparation_id=p.id and a.status='ACTIVE'),0) as a_allocated"
                + " from billing_preparations p"
                + " left join journals j on j.id=p.journal_id"
                + " left join billing_tax_assessments t on t.preparation_id=p.id"
                + where
                + " order by p.created_at desc,p.id desc limit ? offset ?",
            args.toArray())
        .stream()
        .map(this::listView)
        .toList();
  }

  private Map<String, Object> listView(Map<String, Object> raw) {
    var row = norm(raw);
    var result = new LinkedHashMap<String, Object>();
    for (String col :
        List.of(
            "id",
            "kind",
            "source_id",
            "party_id",
            "branch_id",
            "party_name",
            "prepared_on",
            "quantity_basis",
            "reference",
            "evidence_reference",
            "basis_reason",
            "subtotal",
            "status",
            "journal_id",
            "created_by",
            "created_at")) {
      result.put(col, row.get(col));
    }
    result.put("currency", "INR");
    result.put("unit", "TONNE");
    String jStatus = (String) row.get("j_status");
    if (result.get("journal_id") != null) result.put("journal_status", jStatus);
    result.put("ledger_posted", "POSTED".equals(jStatus));
    boolean blocked =
        "DRAFT".equals(jStatus)
            || (row.get("j_active_posted") instanceof Number n && n.intValue() == 1);
    result.put("gst_editable", "READY".equals(result.get("status")) && !blocked);
    if (row.get("t_supply") == null) {
      result.put("tax_treatment", "PENDING_ACCOUNTANT_CONFIGURATION");
      result.put("invoice_issued", false);
      result.put("document_type", "BILLING_PREPARATION");
    } else {
      boolean sales = "SALES_ORDER".equals(result.get("kind"));
      BigDecimal payable = ((BigDecimal) row.get("t_payable_total")).setScale(2, RoundingMode.HALF_UP);
      BigDecimal allocated = ((BigDecimal) row.get("a_allocated")).setScale(2, RoundingMode.HALF_UP);
      BigDecimal outstanding = payable.subtract(allocated);
      String paymentStatus =
          outstanding.signum() == 0
              ? "PAID"
              : allocated.signum() > 0 ? "PARTIALLY_PAID" : "UNPAID";
      result.put("tax_treatment", "ASSESSED");
      result.put("supply", row.get("t_supply"));
      result.put("invoice_number", row.get("t_invoice_number"));
      result.put("invoice_issued", row.get("t_invoice_number") != null);
      result.put("taxable_amount", row.get("t_taxable_amount"));
      result.put("cgst_amount", row.get("t_cgst_amount"));
      result.put("sgst_amount", row.get("t_sgst_amount"));
      result.put("igst_amount", row.get("t_igst_amount"));
      result.put("tax_total", row.get("t_tax_total"));
      result.put("payable_total", payable);
      result.put("allocated_amount", allocated);
      result.put("outstanding_amount", outstanding);
      result.put("payment_status", paymentStatus);
      result.put("document_type", sales ? "TAX_INVOICE" : "SUPPLIER_BILL");
    }
    return result;
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
    if (action.equals("CANCEL")
        && row.get("journal_id") != null
        && journalBlocksCancel((UUID) row.get("journal_id")))
      throw conflict("Reject or reverse the linked journal before cancelling this preparation");
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
            payloadOf(
                id,
                r.periodId(),
                r.entryDate(),
                r.counterAccountId(),
                r.cgstAccountId(),
                r.sgstAccountId(),
                r.igstAccountId(),
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
    var assessedRows =
        db.queryForList("select * from billing_tax_assessments where preparation_id=?", id);
    if (assessedRows.isEmpty())
      throw conflict("Enter the GST treatment on this bill before creating the ledger journal");
    var assessed = norm(assessedRows.getFirst());
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
    BigDecimal payable = (BigDecimal) assessed.get("payable_total");
    boolean sales = partyType.equals("CUSTOMER");
    boolean intra = "INTRA_STATE".equals(assessed.get("supply"));
    String gstType = sales ? "LIABILITY" : "ASSET";
    UUID cgstAccount = gstAccount(r.cgstAccountId(), (BigDecimal) assessed.get("cgst_amount"), gstType, "CGST");
    UUID sgstAccount = gstAccount(r.sgstAccountId(), (BigDecimal) assessed.get("sgst_amount"), gstType, "SGST");
    UUID igstAccount = gstAccount(r.igstAccountId(), (BigDecimal) assessed.get("igst_amount"), gstType, "IGST");
    if (intra && igstAccount != null)
      throw conflict("An intra-state bill does not post IGST");
    if (!intra && (cgstAccount != null || sgstAccount != null))
      throw conflict("An inter-state bill posts IGST only");
    if (cgstAccount != null && cgstAccount.equals(sgstAccount))
      throw conflict("CGST and SGST need separate ledger accounts");
    for (UUID gst : new UUID[] {cgstAccount, sgstAccount, igstAccount}) {
      if (gst != null
          && (gst.equals(partyAccount) || gst.equals(r.counterAccountId())))
        throw conflict("GST account cannot be the party or the income/expense account");
    }
    boolean salesSide = sales;
    var lines = new ArrayList<AccountingDtos.Line>();
    lines.add(
        new AccountingDtos.Line(
            partyAccount,
            salesSide ? payable : BigDecimal.ZERO,
            salesSide ? BigDecimal.ZERO : payable,
            "Party " + partyType.toLowerCase(Locale.ROOT) + " " + row.get("party_name")));
    lines.add(
        new AccountingDtos.Line(
            r.counterAccountId(),
            salesSide ? BigDecimal.ZERO : amount,
            salesSide ? amount : BigDecimal.ZERO,
            r.description().strip()));
    addGst(lines, cgstAccount, salesSide, (BigDecimal) assessed.get("cgst_amount"), "CGST");
    addGst(lines, sgstAccount, salesSide, (BigDecimal) assessed.get("sgst_amount"), "SGST");
    addGst(lines, igstAccount, salesSide, (BigDecimal) assessed.get("igst_amount"), "IGST");
    accounting.journal(
        new AccountingDtos.Journal(
            r.journalId(),
            r.periodId(),
            (UUID) row.get("branch_id"),
            r.entryDate(),
            r.description().strip(),
            r.reference().strip(),
            lines));
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

  private List<Object> payloadOf(Object... values) {
    return new ArrayList<>(Arrays.asList(values));
  }

  private UUID gstAccount(UUID id, BigDecimal amount, String expectedType, String label) {
    if (amount.signum() == 0) {
      if (id != null) throw conflict("This bill has no " + label + " to post");
      return null;
    }
    if (id == null) throw new IllegalArgumentException(label + " account is required");
    var rows = db.queryForList("select * from ledger_accounts where id=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " account not found");
    var account = norm(rows.getFirst());
    if (!Boolean.TRUE.equals(account.get("active"))) throw conflict(label + " account is inactive");
    if (!expectedType.equals(account.get("type")))
      throw conflict(label + " account must be an active " + expectedType + " account");
    if (db.queryForObject(
                "select count(*) from treasury_accounts where account_id=?", Integer.class, id)
            > 0
        || db.queryForObject(
                "select count(*) from party_financial_profiles where account_id=?",
                Integer.class,
                id)
            > 0)
      throw conflict(label + " account cannot be a party or cash/bank account");
    return id;
  }

  private void addGst(
      List<AccountingDtos.Line> lines, UUID account, boolean sales, BigDecimal amount, String label) {
    if (account == null || amount.signum() == 0) return;
    lines.add(
        new AccountingDtos.Line(
            account,
            sales ? BigDecimal.ZERO : amount,
            sales ? amount : BigDecimal.ZERO,
            label + " on this bill"));
  }

  Map<String, Object> tax(UUID id, BillingPreparationController.TaxInput r) {
    lock();
    var row = one(id);
    var payloadItems = new ArrayList<Object>();
    payloadItems.add(id);
    payloadItems.add(r.supply().name());
    payloadItems.add(actor());
    for (var line : r.lines())
      payloadItems.add(
          List.of(line.position(), line.component().name(), line.hsnOrSac().strip(), line.ratePercent()));
    String payload = json.writeValueAsString(payloadItems);
    var existing =
        db.queryForList("select * from billing_tax_assessments where preparation_id=?", id);
    if (!existing.isEmpty()) {
      var old = norm(existing.getFirst());
      if (r.eventId().equals(old.get("event_id")) && payload.equals(old.get("create_payload"))) {
        var result = get(id);
        result.put("replayed", true);
        return result;
      }
    }
    if (db.queryForObject(
            "select count(*) from billing_tax_assessments where event_id=?", Integer.class, r.eventId())
        > 0)
      throw conflict("Tax event ID used with different billing data or actor");
    if (!"READY".equals(row.get("status")))
      throw conflict("Only a READY bill can have GST entered");
    if (actor().equals(row.get("created_by")))
      throw conflict("A different user must enter the GST on this bill");
    UUID current = (UUID) row.get("journal_id");
    if (current != null && journalBlocksCancel(current))
      throw conflict("Reject or reverse the linked journal before changing GST");
    var expected = new LinkedHashMap<String, BigDecimal>();
    for (var line :
        db.queryForList(
            "select position,material_amount,transport_amount from billing_preparation_lines where"
                + " preparation_id=? order by position",
            id)) {
      var n = norm(line);
      int position = ((Number) n.get("position")).intValue();
      BigDecimal material = (BigDecimal) n.get("material_amount");
      BigDecimal transport = (BigDecimal) n.get("transport_amount");
      if (material.signum() > 0) expected.put(position + ":MATERIAL", material);
      if (transport.signum() > 0) expected.put(position + ":TRANSPORT", transport);
    }
    if (r.lines().size() != expected.size())
      throw new IllegalArgumentException(
          "Enter one GST rate for each material and freight amount on this bill");
    boolean intra = r.supply() == BillingPreparationController.Supply.INTRA_STATE;
    BigDecimal taxable = BigDecimal.ZERO;
    BigDecimal cgst = BigDecimal.ZERO;
    BigDecimal sgst = BigDecimal.ZERO;
    BigDecimal igst = BigDecimal.ZERO;
    var computed = new ArrayList<Object[]>();
    var seen = new HashSet<String>();
    for (var line : r.lines()) {
      String key = line.position() + ":" + line.component().name();
      BigDecimal base = expected.get(key);
      if (base == null || !seen.add(key))
        throw new IllegalArgumentException("GST line does not match a taxable amount on this bill");
      BigDecimal tax =
          base.multiply(line.ratePercent()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
      BigDecimal lineCgst = BigDecimal.ZERO;
      BigDecimal lineSgst = BigDecimal.ZERO;
      BigDecimal lineIgst = BigDecimal.ZERO;
      if (intra) {
        lineCgst = tax.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        lineSgst = tax.subtract(lineCgst);
      } else lineIgst = tax;
      taxable = taxable.add(base);
      cgst = cgst.add(lineCgst);
      sgst = sgst.add(lineSgst);
      igst = igst.add(lineIgst);
      computed.add(
          new Object[] {
            line.position(),
            line.component().name(),
            line.hsnOrSac().strip(),
            line.ratePercent(),
            base,
            lineCgst,
            lineSgst,
            lineIgst
          });
    }
    BigDecimal taxTotal = cgst.add(sgst).add(igst);
    BigDecimal payable = taxable.add(taxTotal);
    String invoice = null;
    if (!existing.isEmpty()) invoice = (String) norm(existing.getFirst()).get("invoice_number");
    else if ("SALES_ORDER".equals(row.get("kind"))) {
      int fyStart = indianFinancialYearStart(day(row.get("prepared_on")));
      String fyCode = indianFinancialYearCode(fyStart);
      var branchProfile = branches.profile((UUID) row.get("branch_id"));
      String stateCode = branchProfile.stateCode();
      if (stateCode != null && !stateCode.isBlank()) {
        var counters =
            db.queryForList(
                "select last_number from state_invoice_counters where state_code=? and fy_start=? for update",
                stateCode,
                fyStart);
        int next;
        if (counters.isEmpty()) {
          next = 1;
          db.update(
              "insert into state_invoice_counters(state_code,fy_start,last_number) values(?,?,?)",
              stateCode,
              fyStart,
              next);
        } else {
          next = ((Number) norm(counters.getFirst()).get("last_number")).intValue() + 1;
          db.update(
              "update state_invoice_counters set last_number=? where state_code=? and fy_start=?",
              next,
              stateCode,
              fyStart);
        }
        // CGST Rule 46(b): max 16 characters -> e.g. SI-2627-08-00001
        invoice = "SI-" + fyCode + "-" + stateCode + "-" + String.format("%05d", next);
      } else {
        var counters =
            db.queryForList(
                "select last_number from sales_invoice_counters where calendar_year=? for update",
                fyStart);
        int next;
        if (counters.isEmpty()) {
          next = 1;
          db.update(
              "insert into sales_invoice_counters(calendar_year,last_number) values(?,?)",
              fyStart,
              next);
        } else {
          next = ((Number) norm(counters.getFirst()).get("last_number")).intValue() + 1;
          db.update(
              "update sales_invoice_counters set last_number=? where calendar_year=?",
              next,
              fyStart);
        }
        invoice = "SI-" + fyCode + "-" + String.format("%05d", next);
      }
    }
    if (!existing.isEmpty()) {
      db.update("delete from billing_tax_lines where preparation_id=?", id);
      db.update("delete from billing_tax_assessments where preparation_id=?", id);
    }
    db.update(
        "insert into billing_tax_assessments(preparation_id,supply,invoice_number,taxable_amount,"
            + " cgst_amount,sgst_amount,igst_amount,tax_total,payable_total,event_id,assessed_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        r.supply().name(),
        invoice,
        taxable,
        cgst,
        sgst,
        igst,
        taxTotal,
        payable,
        r.eventId(),
        actor(),
        payload);
    for (var line : computed)
      db.update(
          "insert into billing_tax_lines(preparation_id,position,component,hsn_or_sac,rate_percent,"
              + " taxable_amount,cgst_amount,sgst_amount,igst_amount) values(?,?,?,?,?,?,?,?,?)",
          id,
          line[0],
          line[1],
          line[2],
          line[3],
          line[4],
          line[5],
          line[6],
          line[7]);
    audit.record(
        "BILLING_TAX_ASSESSED", "BILLING_PREPARATION", id, "Supply=" + r.supply().name());
    return get(id);
  }

  static int indianFinancialYearStart(LocalDate date) {
    return date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
  }

  static String indianFinancialYearCode(int fyStart) {
    return String.format("%02d%02d", Math.floorMod(fyStart, 100), Math.floorMod(fyStart + 1, 100));
  }

  private LocalDate day(Object v) {
    if (v instanceof LocalDate d) return d;
    if (v instanceof java.sql.Date d) return d.toLocalDate();
    if (v instanceof java.sql.Timestamp t)
      return t.toInstant().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
    return ((java.util.Date) v).toInstant().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
  }
}
