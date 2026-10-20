package com.yfctransport.accounting;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.customers.CustomerRegistry;
import com.yfctransport.suppliers.SupplierRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class PartyFinanceService {
  private final JdbcTemplate db;
  private final AccountingService accounting;
  private final CustomerRegistry customers;
  private final SupplierRegistry suppliers;
  private final AuditTrail audit;
  private final ObjectMapper json;

  PartyFinanceService(
      JdbcTemplate db,
      AccountingService accounting,
      CustomerRegistry customers,
      SupplierRegistry suppliers,
      AuditTrail audit,
      ObjectMapper json) {
    this.db = db;
    this.accounting = accounting;
    this.customers = customers;
    this.suppliers = suppliers;
    this.audit = audit;
    this.json = json;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private void lock() {
    db.queryForObject("select id from accounting_guard where id=1 for update", Integer.class);
  }

  private Map<String, Object> normalize(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var r = normalize(row);
    r.remove("create_payload");
    r.remove("payload");
    return r;
  }

  private Map<String, Object> one(UUID id) {
    var rows = db.queryForList("select * from party_financial_profiles where id=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Party financial profile not found");
    return normalize(rows.getFirst());
  }

  private ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }

  private String n(BigDecimal amount) {
    return amount == null ? null : amount.stripTrailingZeros().toPlainString();
  }

  Map<String, Object> get(UUID id) {
    var result = view(one(id));
    result.put(
        "terms_history",
        db
            .queryForList(
                "select * from party_financial_events where profile_id=? order by to_version", id)
            .stream()
            .map(this::view)
            .toList());
    return result;
  }

  Map<String, Object> lookup(PartyFinanceController.Type type, UUID partyId) {
    String column = type == PartyFinanceController.Type.CUSTOMER ? "customer_id" : "supplier_id";
    var ids =
        db.query(
            "select id from party_financial_profiles where " + column + "=?",
            (r, n) -> r.getObject(1, UUID.class),
            partyId);
    if (ids.isEmpty())
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Party financial profile not configured");
    return get(ids.getFirst());
  }

  List<Map<String, Object>> list(int page, int size) {
    return db
        .queryForList(
            "select * from party_financial_profiles order by created_at,id limit ? offset ?",
            size,
            (long) page * size)
        .stream()
        .map(this::view)
        .toList();
  }

  private void validateLimit(String type, BigDecimal limit) {
    if (type.equals("SUPPLIER") && limit != null)
      throw new IllegalArgumentException(
          "Supplier credit limits are outside this workflow; omit creditLimit");
  }

  Map<String, Object> create(PartyFinanceController.Input r) {
    lock();
    String payload =
        json.writeValueAsString(
            Arrays.asList(
                r.partyType(),
                r.partyId(),
                r.accountId(),
                r.paymentTermsDays(),
                n(r.creditLimit())));
    if (db.queryForObject(
            "select count(*) from party_financial_profiles where id=?",
            Integer.class,
            r.profileId())
        > 0) {
      if (!payload.equals(one(r.profileId()).get("create_payload")))
        throw conflict("Profile ID used with different data");
      var replay = get(r.profileId());
      replay.put("replayed", true);
      return replay;
    }
    validateLimit(r.partyType().name(), r.creditLimit());
    var accounts = db.queryForList("select * from ledger_accounts where id=?", r.accountId());
    if (accounts.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger account not found");
    var account = normalize(accounts.getFirst());
    String required = r.partyType() == PartyFinanceController.Type.CUSTOMER ? "ASSET" : "LIABILITY";
    if (!Boolean.TRUE.equals(account.get("active")) || !required.equals(account.get("type")))
      throw conflict("An active " + required + " ledger account is required");
    if (db.queryForObject(
            "select count(*) from treasury_accounts where account_id=?",
            Integer.class,
            r.accountId())
        > 0) throw conflict("Registered cash/bank accounts cannot be party accounts");
    if (db.queryForObject(
            "select count(*) from journal_lines where account_id=?", Integer.class, r.accountId())
        > 0)
      throw conflict("Use a fresh account without journal history, including draft/rejected lines");
    String name =
        r.partyType() == PartyFinanceController.Type.CUSTOMER
            ? customers.name(r.partyId())
            : suppliers.name(r.partyId());
    db.update(
        "insert into"
            + " party_financial_profiles(id,party_type,customer_id,supplier_id,account_id,party_name,payment_terms_days,credit_limit,create_payload,created_by)"
            + " values(?,?,?,?,?,?,?,?,?,?)",
        r.profileId(),
        r.partyType().name(),
        r.partyType() == PartyFinanceController.Type.CUSTOMER ? r.partyId() : null,
        r.partyType() == PartyFinanceController.Type.SUPPLIER ? r.partyId() : null,
        r.accountId(),
        name,
        r.paymentTermsDays(),
        r.creditLimit(),
        payload,
        actor());
    audit.record(
        "PARTY_FINANCE_CREATED",
        "PARTY_FINANCE",
        r.profileId(),
        "Type=" + r.partyType() + "; account=" + r.accountId());
    return get(r.profileId());
  }

  Map<String, Object> terms(UUID id, PartyFinanceController.Terms r) {
    lock();
    var profile = one(id);
    String payload =
        json.writeValueAsString(
            Arrays.asList(
                id, r.version(), r.paymentTermsDays(), n(r.creditLimit()), r.reason(), actor()));
    var events =
        db.queryForList("select payload from party_financial_events where id=?", r.eventId());
    if (!events.isEmpty()) {
      if (!payload.equals(events.getFirst().get("payload")))
        throw conflict("Terms event ID used with different data or actor");
      var result = get(id);
      result.put("replayed", true);
      return result;
    }
    if (((Number) profile.get("version")).intValue() != r.version())
      throw conflict("Profile version changed; reload before editing terms");
    validateLimit((String) profile.get("party_type"), r.creditLimit());
    BigDecimal old = (BigDecimal) profile.get("credit_limit");
    boolean same =
        old == null
            ? r.creditLimit() == null
            : r.creditLimit() != null && old.compareTo(r.creditLimit()) == 0;
    if (((Number) profile.get("payment_terms_days")).intValue() == r.paymentTermsDays() && same)
      throw new IllegalArgumentException("Commercial terms did not change");
    db.update(
        "insert into"
            + " party_financial_events(id,profile_id,from_version,to_version,previous_terms_days,new_terms_days,previous_credit_limit,new_credit_limit,reason,actor,payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?)",
        r.eventId(),
        id,
        r.version(),
        r.version() + 1,
        profile.get("payment_terms_days"),
        r.paymentTermsDays(),
        old,
        r.creditLimit(),
        r.reason(),
        actor(),
        payload);
    db.update(
        "update party_financial_profiles set payment_terms_days=?,credit_limit=?,version=version+1"
            + " where id=?",
        r.paymentTermsDays(),
        r.creditLimit(),
        id);
    audit.record("PARTY_FINANCE_TERMS", "PARTY_FINANCE", id, "Event=" + r.eventId());
    return get(id);
  }

  private Map<String, Object> balance(String type, BigDecimal signed) {
    var r = new LinkedHashMap<String, Object>();
    r.put("signed_balance", signed);
    String kind;
    if (signed.signum() == 0) kind = "ZERO_BALANCE";
    else if (type.equals("CUSTOMER")) kind = signed.signum() > 0 ? "RECEIVABLE" : "CUSTOMER_CREDIT";
    else kind = signed.signum() < 0 ? "PAYABLE" : "SUPPLIER_ADVANCE";
    r.put("balance_kind", kind);
    r.put("balance_amount", signed.abs());
    return r;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  List<Map<String, Object>> balances(LocalDate asOf, UUID branch, int page, int size) {
    String condition =
        "j.status='POSTED' and j.entry_date<=?" + (branch == null ? "" : " and j.branch_id=?");
    String sql =
        "select"
            + " p.id,p.party_type,p.party_name,p.customer_id,p.supplier_id,p.account_id,p.payment_terms_days,p.credit_limit,p.version,a.code"
            + " as account_code,a.active as account_active,coalesce(sum(case when "
            + condition
            + " then l.debit-l.credit else 0 end),0) as signed_balance from"
            + " party_financial_profiles p join ledger_accounts a on a.id=p.account_id left join"
            + " journal_lines l on l.account_id=p.account_id left join journals j on"
            + " j.id=l.journal_id group by"
            + " p.id,p.party_type,p.party_name,p.customer_id,p.supplier_id,p.account_id,p.payment_terms_days,p.credit_limit,p.version,a.code,a.active,p.created_at"
            + " order by p.created_at,p.id limit ? offset ?";
    var args = new ArrayList<Object>();
    args.add(asOf);
    if (branch != null) args.add(branch);
    args.add(size);
    args.add((long) page * size);
    return db.queryForList(sql, args.toArray()).stream()
        .map(this::normalize)
        .map(
            row -> {
              row.putAll(
                  balance((String) row.get("party_type"), (BigDecimal) row.get("signed_balance")));
              row.put("currency", "INR");
              row.put("as_of", asOf);
              return row;
            })
        .toList();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Map<String, Object> ledger(
      UUID id, LocalDate from, LocalDate to, UUID branch, int page, int size) {
    var profile = one(id);
    var ledger =
        new LinkedHashMap<>(
            accounting.ledger((UUID) profile.get("account_id"), from, to, branch, page, size));
    ledger.put("profile", view(profile));
    ledger.putAll(
        balance((String) profile.get("party_type"), (BigDecimal) ledger.get("closingBalance")));
    return ledger;
  }
}
