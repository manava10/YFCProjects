package com.yfctransport.accounting;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import java.math.BigDecimal;
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
class MoneyService {
  private final JdbcTemplate db;
  private final AccountingService accounting;
  private final BranchRegistry branches;
  private final AuditTrail audit;
  private final ObjectMapper json;

  MoneyService(
      JdbcTemplate db,
      AccountingService accounting,
      BranchRegistry branches,
      AuditTrail audit,
      ObjectMapper json) {
    this.db = db;
    this.accounting = accounting;
    this.branches = branches;
    this.audit = audit;
    this.json = json;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private void lock() {
    db.queryForObject("select id from accounting_guard where id=1 for update", Integer.class);
  }

  private Map<String, Object> norm(Map<String, Object> r) {
    var result = new LinkedHashMap<String, Object>();
    r.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Map<String, Object> view(Map<String, Object> r) {
    var result = norm(r);
    result.remove("create_payload");
    result.remove("reference_key");
    return result;
  }

  private Map<String, Object> one(String table, String key, UUID id) {
    var rows = db.queryForList("select * from " + table + " where " + key + "=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Money/financial record not found");
    return norm(rows.getFirst());
  }

  private ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }

  Map<String, Object> register(MoneyController.Registration r) {
    lock();
    String payload =
        json.writeValueAsString(List.of(r.branchId(), r.kind(), r.reference().strip()));
    if (db.queryForObject(
            "select count(*) from treasury_accounts where account_id=?",
            Integer.class,
            r.accountId())
        > 0) {
      var old = one("treasury_accounts", "account_id", r.accountId());
      if (!payload.equals(old.get("create_payload")))
        throw conflict("Money account already registered with different details");
      var result = view(old);
      result.put("replayed", true);
      return result;
    }
    var account = one("ledger_accounts", "id", r.accountId());
    branches.requireActive(r.branchId());
    if (!"ASSET".equals(account.get("type")) || !Boolean.TRUE.equals(account.get("active")))
      throw conflict("Money account must be an active ASSET account");
    if (db.queryForObject(
            "select count(*) from party_financial_profiles where account_id=?",
            Integer.class,
            r.accountId())
        > 0) throw conflict("Party accounts cannot be cash/bank accounts");
    if (db.queryForObject(
            "select count(*) from journal_lines where account_id=?", Integer.class, r.accountId())
        > 0) throw conflict("Register a fresh account without journal history");
    db.update(
        "insert into"
            + " treasury_accounts(account_id,branch_id,kind,reference,create_payload,created_by)"
            + " values(?,?,?,?,?,?)",
        r.accountId(),
        r.branchId(),
        r.kind().name(),
        r.reference().strip(),
        payload,
        actor());
    audit.record(
        "MONEY_ACCOUNT_REGISTERED",
        "LEDGER_ACCOUNT",
        r.accountId(),
        "Kind=" + r.kind() + "; branch=" + r.branchId());
    return view(one("treasury_accounts", "account_id", r.accountId()));
  }

  List<Map<String, Object>> accounts(int page, int size) {
    return db
        .queryForList(
            "select"
                + " t.account_id,t.branch_id,t.kind,t.reference,t.created_by,t.created_at,a.code,a.name,a.active"
                + " from treasury_accounts t join ledger_accounts a on a.id=t.account_id order by"
                + " t.created_at,t.account_id limit ? offset ?",
            size,
            (long) page * size)
        .stream()
        .map(this::view)
        .toList();
  }

  Map<String, Object> create(MoneyController.Input r) {
    lock();
    String ref = r.reference().strip(),
        key = ref.toUpperCase(Locale.ROOT),
        evidence = r.evidenceReference().strip();
    if (key.length() > 300)
      throw new IllegalArgumentException("Normalised money reference exceeds 300 characters");
    String payload =
        json.writeValueAsString(
            List.of(
                r.profileId(),
                r.moneyAccountId(),
                r.periodId(),
                r.branchId(),
                r.kind(),
                r.entryDate(),
                r.amount().stripTrailingZeros().toPlainString(),
                key,
                evidence));
    if (db.queryForObject(
            "select count(*) from money_vouchers where id=?", Integer.class, r.voucherId())
        > 0) {
      if (!payload.equals(one("money_vouchers", "id", r.voucherId()).get("create_payload")))
        throw conflict("Voucher ID used with different data");
      var result = get(r.voucherId());
      result.put("replayed", true);
      return result;
    }
    if (db.queryForObject("select count(*) from journals where id=?", Integer.class, r.voucherId())
        > 0) throw conflict("Voucher ID already used by a journal");
    var treasury = one("treasury_accounts", "account_id", r.moneyAccountId());
    if (!r.branchId().equals(treasury.get("branch_id")))
      throw conflict("Money account belongs to another branch");
    var party = one("party_financial_profiles", "id", r.profileId());
    String expected = r.kind().name().startsWith("CUSTOMER") ? "CUSTOMER" : "SUPPLIER";
    if (!expected.equals(party.get("party_type")))
      throw conflict("Voucher kind does not match the party type");
    if (db.queryForObject(
            "select count(*) from voucher_reference_claims where money_account_id=? and"
                + " reference_key=?",
            Integer.class,
            r.moneyAccountId(),
            key)
        > 0) throw conflict("Reference already reserved/recorded for this cash/bank account");
    boolean incoming =
        r.kind() == MoneyController.Kind.CUSTOMER_RECEIPT
            || r.kind() == MoneyController.Kind.SUPPLIER_REFUND;
    UUID partyAccount = (UUID) party.get("account_id");
    var moneyLine =
        new AccountingDtos.Line(
            r.moneyAccountId(),
            incoming ? r.amount() : BigDecimal.ZERO,
            incoming ? BigDecimal.ZERO : r.amount(),
            r.kind().name());
    var partyLine =
        new AccountingDtos.Line(
            partyAccount,
            incoming ? BigDecimal.ZERO : r.amount(),
            incoming ? r.amount() : BigDecimal.ZERO,
            r.kind().name());
    accounting.journal(
        new AccountingDtos.Journal(
            r.voucherId(),
            r.periodId(),
            r.branchId(),
            r.entryDate(),
            r.kind().name() + "; profile=" + r.profileId(),
            ref,
            List.of(moneyLine, partyLine)));
    db.update(
        "insert into"
            + " money_vouchers(id,profile_id,money_account_id,kind,amount,reference,reference_key,evidence_reference,create_payload,created_by)"
            + " values(?,?,?,?,?,?,?,?,?,?)",
        r.voucherId(),
        r.profileId(),
        r.moneyAccountId(),
        r.kind().name(),
        r.amount(),
        ref,
        key,
        evidence,
        payload,
        actor());
    db.update(
        "insert into voucher_reference_claims(money_account_id,reference_key,voucher_id)"
            + " values(?,?,?)",
        r.moneyAccountId(),
        key,
        r.voucherId());
    audit.record("MONEY_VOUCHER_SUBMITTED", "MONEY_VOUCHER", r.voucherId(), "Kind=" + r.kind());
    return get(r.voucherId());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Map<String, Object> get(UUID id) {
    var result = view(one("money_vouchers", "id", id));
    var journal = accounting.journal(id);
    result.put("journal", journal);
    var reversals =
        db
            .queryForList(
                "select id,status,number,entry_date from journals where reverses_id=? order by"
                    + " submitted_at,id",
                id)
            .stream()
            .map(this::norm)
            .toList();
    result.put("reversals", reversals);
    boolean reversed = reversals.stream().anyMatch(row -> "POSTED".equals(row.get("status")));
    result.put("effective_status", reversed ? "REVERSED" : journal.get("status"));
    result.put("currency", "INR");
    result.put("bank_transfer_executed_by_system", false);
    return result;
  }

  List<Map<String, Object>> list(int page, int size) {
    return db
        .queryForList(
            "select"
                + " v.id,v.profile_id,v.money_account_id,v.kind,v.amount,v.reference,v.evidence_reference,v.created_by,v.created_at,j.status,j.number,j.entry_date,j.branch_id,case"
                + " when exists(select 1 from journals r where r.reverses_id=j.id and"
                + " r.status='POSTED') then 'REVERSED' else j.status end as effective_status from"
                + " money_vouchers v join journals j on j.id=v.id order by v.created_at,v.id limit"
                + " ? offset ?",
            size,
            (long) page * size)
        .stream()
        .map(this::view)
        .toList();
  }

  Map<String, Object> decide(UUID id, AccountingDtos.Decision r, boolean post) {
    lock();
    one("money_vouchers", "id", id);
    var decision = accounting.decide(id, r, post);
    var result = get(id);
    if (Boolean.TRUE.equals(decision.get("replayed"))) result.put("replayed", true);
    return result;
  }

  Map<String, Object> reverse(UUID id, AccountingDtos.Reversal r) {
    lock();
    one("money_vouchers", "id", id);
    return accounting.reverse(id, r);
  }
}
