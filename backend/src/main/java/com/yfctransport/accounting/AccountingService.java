package com.yfctransport.accounting;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
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
class AccountingService {
  private final JdbcTemplate db;
  private final AuditTrail audit;
  private final BranchRegistry branches;
  private final Clock clock;
  private final ObjectMapper json;

  AccountingService(
      JdbcTemplate db, AuditTrail audit, BranchRegistry branches, Clock clock, ObjectMapper json) {
    this.db = db;
    this.audit = audit;
    this.branches = branches;
    this.clock = clock;
    this.json = json;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }

  private void lock() {
    db.queryForObject("select id from accounting_guard where id=1 for update", Integer.class);
  }

  private Map<String, Object> normalized(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Map<String, Object> one(String table, UUID id) {
    var rows = db.queryForList("select * from " + table + " where id=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Accounting record not found");
    return normalized(rows.getFirst());
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = new LinkedHashMap<>(row);
    result.remove("create_payload");
    result.remove("reversal_claim");
    return result;
  }

  private LocalDate day(Object v) {
    return v instanceof LocalDate d ? d : ((java.sql.Date) v).toLocalDate();
  }

  private String money(BigDecimal n) {
    return n.stripTrailingZeros().toPlainString();
  }

  private String payload(Object... values) {
    return json.writeValueAsString(Arrays.asList(values));
  }

  private Map<String, Object> retry(String table, UUID id, String payload) {
    if (db.queryForObject("select count(*) from " + table + " where id=?", Integer.class, id) == 0)
      return null;
    var old = one(table, id);
    if (!payload.equals(old.get("create_payload")))
      throw conflict("ID already used with different data");
    var result = table.equals("journals") ? journal(id) : view(old);
    result.put("replayed", true);
    return result;
  }

  List<Map<String, Object>> list(String table, int page, int size) {
    return db
        .queryForList(
            "select * from "
                + table
                + " order by "
                + (table.equals("journals") ? "submitted_at" : "created_at")
                + ",id limit ? offset ?",
            size,
            (long) page * size)
        .stream()
        .map(this::normalized)
        .map(this::view)
        .toList();
  }

  Map<String, Object> account(AccountingDtos.Account r) {
    lock();
    String code = r.code().toUpperCase(Locale.ROOT);
    String payload = payload(code, r.name(), r.type());
    var replay = retry("ledger_accounts", r.accountId(), payload);
    if (replay != null) return replay;
    db.update(
        "insert into ledger_accounts(id,code,name,type,create_payload,created_by)"
            + " values(?,?,?,?,?,?)",
        r.accountId(),
        code,
        r.name(),
        r.type().name(),
        payload,
        actor());
    audit.record("LEDGER_ACCOUNT_CREATED", "LEDGER_ACCOUNT", r.accountId(), "Code=" + code);
    return view(one("ledger_accounts", r.accountId()));
  }

  Map<String, Object> account(UUID id) {
    var result = view(one("ledger_accounts", id));
    result.put(
        "status_events",
        db
            .queryForList(
                "select id,old_active,new_active,reason,actor,occurred_at from"
                    + " ledger_account_status_events where account_id=? order by occurred_at,id",
                id)
            .stream()
            .map(this::normalized)
            .toList());
    return result;
  }

  Map<String, Object> status(UUID id, AccountingDtos.AccountStatus r) {
    lock();
    var old = one("ledger_accounts", id);
    boolean current = (Boolean) old.get("active");
    if (current == r.active()) return view(old);
    if (current != r.expectedActive()) throw conflict("Account status changed; reload first");
    UUID event = UUID.randomUUID();
    db.update(
        "insert into ledger_account_status_events(id,account_id,old_active,new_active,reason,actor)"
            + " values(?,?,?,?,?,?)",
        event,
        id,
        current,
        r.active(),
        r.reason(),
        actor());
    db.update("update ledger_accounts set active=? where id=?", r.active(), id);
    audit.record("LEDGER_ACCOUNT_STATUS", "LEDGER_ACCOUNT", id, "Event=" + event);
    return view(one("ledger_accounts", id));
  }

  Map<String, Object> period(AccountingDtos.Period r) {
    lock();
    String code = r.code().toUpperCase(Locale.ROOT);
    String payload = payload(code, r.startsOn(), r.endsOn());
    var replay = retry("financial_periods", r.periodId(), payload);
    if (replay != null) return replay;
    if (r.startsOn().isAfter(r.endsOn()))
      throw new IllegalArgumentException("Period start must not follow its end");
    if (db.queryForObject(
            "select count(*) from financial_periods where starts_on<=? and ends_on>=?",
            Integer.class,
            r.endsOn(),
            r.startsOn())
        > 0) throw conflict("Financial periods cannot overlap, including closed periods");
    db.update(
        "insert into financial_periods(id,code,starts_on,ends_on,create_payload,created_by)"
            + " values(?,?,?,?,?,?)",
        r.periodId(),
        code,
        r.startsOn(),
        r.endsOn(),
        payload,
        actor());
    audit.record("FINANCIAL_PERIOD_CREATED", "FINANCIAL_PERIOD", r.periodId(), "Code=" + code);
    return view(one("financial_periods", r.periodId()));
  }

  private void periodDate(UUID id, LocalDate date) {
    var p = one("financial_periods", id);
    if (!"OPEN".equals(p.get("status"))) throw conflict("Financial period is closed");
    if (date.isBefore(day(p.get("starts_on"))) || date.isAfter(day(p.get("ends_on"))))
      throw new IllegalArgumentException("Journal date must be within its financial period");
    if (date.isAfter(LocalDate.now(clock)))
      throw new IllegalArgumentException("Cannot post or submit a future-dated journal");
  }

  private boolean replayEvent(UUID id, UUID target, String type, String action, String reason) {
    if (db.queryForObject("select count(*) from accounting_events where id=?", Integer.class, id)
        == 0) return false;
    var e = one("accounting_events", id);
    if (!target.equals(e.get("target_id"))
        || !type.equals(e.get("target_type"))
        || !action.equals(e.get("action"))
        || !reason.equals(e.get("reason"))
        || !actor().equals(e.get("actor")))
      throw conflict("Event ID already used with different data or actor");
    return true;
  }

  private void event(UUID id, UUID target, String type, String action, String reason) {
    db.update(
        "insert into accounting_events(id,target_id,target_type,action,reason,actor)"
            + " values(?,?,?,?,?,?)",
        id,
        target,
        type,
        action,
        reason,
        actor());
  }

  Map<String, Object> close(UUID id, AccountingDtos.Decision r) {
    lock();
    var p = one("financial_periods", id);
    if (replayEvent(r.eventId(), id, "PERIOD", "CLOSE", r.reason())) {
      var result = view(p);
      result.put("replayed", true);
      return result;
    }
    if (!"OPEN".equals(p.get("status"))) throw conflict("Period already closed");
    if (!day(p.get("ends_on")).isBefore(LocalDate.now(clock)))
      throw conflict("Only a period ending before today may be closed");
    if (db.queryForObject(
            "select count(*) from journals where period_id=? and status='DRAFT'", Integer.class, id)
        > 0) throw conflict("Post or reject all draft journals before closing");
    event(r.eventId(), id, "PERIOD", "CLOSE", r.reason());
    db.update(
        "update financial_periods set status='CLOSED',closed_by=?,closed_at=?,close_reason=? where"
            + " id=?",
        actor(),
        java.sql.Timestamp.from(clock.instant()),
        r.reason(),
        id);
    audit.record("FINANCIAL_PERIOD_CLOSED", "FINANCIAL_PERIOD", id, "Event=" + r.eventId());
    return view(one("financial_periods", id));
  }

  private List<Object> canonicalLines(List<AccountingDtos.Line> lines) {
    return lines.stream()
        .map(
            l ->
                (Object) List.of(l.accountId(), money(l.debit()), money(l.credit()), l.narration()))
        .toList();
  }

  Map<String, Object> journal(AccountingDtos.Journal r) {
    lock();
    String payload =
        payload(
            "MANUAL",
            r.periodId(),
            r.branchId(),
            r.entryDate(),
            r.description(),
            r.reference(),
            canonicalLines(r.lines()));
    var replay = retry("journals", r.journalId(), payload);
    if (replay != null) return replay;
    return insert(r, null, payload);
  }

  private Map<String, Object> insert(AccountingDtos.Journal r, UUID reverses, String payload) {
    periodDate(r.periodId(), r.entryDate());
    branches.requireActive(r.branchId());
    var accountIds = r.lines().stream().map(AccountingDtos.Line::accountId).distinct().toList();
    var treasuryRows =
        db.queryForList(
            "select account_id,branch_id from treasury_accounts where account_id in ("
                + String.join(",", Collections.nCopies(accountIds.size(), "?"))
                + ")",
            accountIds.toArray());
    for (var raw : treasuryRows)
      if (!r.branchId().equals(normalized(raw).get("branch_id")))
        throw conflict("Cash/bank account belongs to another branch");
    BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
    for (var line : r.lines()) {
      boolean d = line.debit().signum() > 0, c = line.credit().signum() > 0;
      if (d == c)
        throw new IllegalArgumentException(
            "Each journal line must contain one positive debit or credit, with the other zero");
      if (!Boolean.TRUE.equals(one("ledger_accounts", line.accountId()).get("active")))
        throw conflict("Journal account is inactive");
      debit = debit.add(line.debit());
      credit = credit.add(line.credit());
    }
    if (debit.signum() <= 0 || debit.compareTo(credit) != 0)
      throw new IllegalArgumentException("Journal debits and credits must balance exactly");
    db.update(
        "insert into"
            + " journals(id,period_id,branch_id,entry_date,description,reference,total_debit,total_credit,submitted_by,reverses_id,reversal_claim,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?)",
        r.journalId(),
        r.periodId(),
        r.branchId(),
        r.entryDate(),
        r.description(),
        r.reference(),
        debit,
        credit,
        actor(),
        reverses,
        reverses,
        payload);
    int position = 0;
    for (var line : r.lines())
      db.update(
          "insert into journal_lines(journal_id,position,account_id,debit,credit,narration)"
              + " values(?,?,?,?,?,?)",
          r.journalId(),
          ++position,
          line.accountId(),
          line.debit(),
          line.credit(),
          line.narration());
    audit.record(
        "JOURNAL_SUBMITTED",
        "JOURNAL",
        r.journalId(),
        reverses == null ? "Manual balanced journal" : "Reverses=" + reverses);
    return journal(r.journalId());
  }

  Map<String, Object> journal(UUID id) {
    var result = view(one("journals", id));
    result.put(
        "lines",
        db
            .queryForList(
                "select l.position,l.account_id,a.code as account_code,a.name as"
                    + " account_name,l.debit,l.credit,l.narration from journal_lines l join"
                    + " ledger_accounts a on a.id=l.account_id where journal_id=? order by"
                    + " position",
                id)
            .stream()
            .map(this::normalized)
            .toList());
    result.put(
        "events",
        db
            .queryForList(
                "select id,action,reason,actor,occurred_at from accounting_events where"
                    + " target_type='JOURNAL' and target_id=? order by occurred_at,id",
                id)
            .stream()
            .map(this::normalized)
            .toList());
    return result;
  }

  Map<String, Object> decide(UUID id, AccountingDtos.Decision r, boolean post) {
    lock();
    var j = one("journals", id);
    String action = post ? "POST" : "REJECT";
    if (replayEvent(r.eventId(), id, "JOURNAL", action, r.reason())) {
      var result = journal(id);
      result.put("replayed", true);
      return result;
    }
    if (!"DRAFT".equals(j.get("status"))) throw conflict("Only a draft journal can be reviewed");
    if (actor().equals(j.get("submitted_by")))
      throw conflict("A different user must post or reject the journal");
    String number = null;
    if (post) {
      periodDate((UUID) j.get("period_id"), day(j.get("entry_date")));
      branches.requireActive((UUID) j.get("branch_id"));
      var lines = db.queryForList("select * from journal_lines where journal_id=?", id);
      BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
      for (var raw : lines) {
        var line = normalized(raw);
        if (!Boolean.TRUE.equals(
            one("ledger_accounts", (UUID) line.get("account_id")).get("active")))
          throw conflict("Journal account is inactive");
        debit = debit.add((BigDecimal) line.get("debit"));
        credit = credit.add((BigDecimal) line.get("credit"));
      }
      if (lines.size() < 2
          || debit.signum() <= 0
          || debit.compareTo(credit) != 0
          || debit.compareTo((BigDecimal) j.get("total_debit")) != 0
          || credit.compareTo((BigDecimal) j.get("total_credit")) != 0)
        throw conflict("Journal balance verification failed");
      validateMoneyPosting(id, (UUID) j.get("branch_id"), day(j.get("entry_date")));
      long sequence =
          db.queryForObject("select next_number from accounting_guard where id=1", Long.class);
      number = String.format(Locale.ROOT, "JV-%08d", sequence);
      db.update("update accounting_guard set next_number=next_number+1 where id=1");
    }
    event(r.eventId(), id, "JOURNAL", action, r.reason());
    db.update(
        "update journals set"
            + " status=?,number=?,reviewed_by=?,reviewed_at=?,review_reason=?,reversal_claim=?"
            + " where id=?",
        post ? "POSTED" : "REJECTED",
        number,
        actor(),
        java.sql.Timestamp.from(clock.instant()),
        r.reason(),
        post ? j.get("reversal_claim") : null,
        id);
    if (!post) db.update("delete from voucher_reference_claims where voucher_id=?", id);
    audit.record(
        post ? "JOURNAL_POSTED" : "JOURNAL_REJECTED", "JOURNAL", id, "Event=" + r.eventId());
    return journal(id);
  }

  private void validateMoneyPosting(UUID journal, UUID branch, LocalDate entryDate) {
    var money =
        db.queryForList(
            "select t.account_id,t.branch_id,t.kind,sum(l.debit-l.credit) as delta from"
                + " treasury_accounts t join journal_lines l on l.account_id=t.account_id where"
                + " l.journal_id=? group by t.account_id,t.branch_id,t.kind",
            journal);
    for (var raw : money) {
      var row = normalized(raw);
      if (!branch.equals(row.get("branch_id")))
        throw conflict("Cash/bank account belongs to another branch");
      if ("CASH".equals(row.get("kind"))) {
        var daily = new TreeMap<LocalDate, BigDecimal>();
        for (var value :
            db.queryForList(
                "select j.entry_date,sum(l.debit-l.credit) as net from journal_lines l join"
                    + " journals j on j.id=l.journal_id where l.account_id=? and j.status='POSTED'"
                    + " group by j.entry_date",
                row.get("account_id"))) {
          var normalized = normalized(value);
          daily.put(day(normalized.get("entry_date")), (BigDecimal) normalized.get("net"));
        }
        daily.merge(entryDate, (BigDecimal) row.get("delta"), BigDecimal::add);
        BigDecimal running = BigDecimal.ZERO;
        for (var value : daily.entrySet()) {
          running = running.add(value.getValue());
          if (running.signum() < 0)
            throw conflict("Cash book would become negative on " + value.getKey());
        }
      }
    }
    var vouchers =
        db.queryForList(
            "select v.kind,v.amount,p.account_id from money_vouchers v join"
                + " party_financial_profiles p on p.id=v.profile_id where v.id=?",
            journal);
    if (!vouchers.isEmpty()) {
      var v = normalized(vouchers.getFirst());
      String kind = (String) v.get("kind");
      if (kind.equals("CUSTOMER_REFUND") || kind.equals("SUPPLIER_REFUND")) {
        BigDecimal signed =
            db.queryForObject(
                "select coalesce(sum(l.debit-l.credit),0) from journal_lines l join journals j on"
                    + " j.id=l.journal_id where l.account_id=? and j.status='POSTED' and"
                    + " j.branch_id=? and j.entry_date<=?",
                BigDecimal.class,
                v.get("account_id"),
                branch,
                entryDate);
        BigDecimal available = kind.equals("CUSTOMER_REFUND") ? signed.negate() : signed;
        if (available.compareTo((BigDecimal) v.get("amount")) < 0)
          throw conflict(
              "Refund exceeds the branch's posted customer credit or supplier advance at this"
                  + " date");
      }
    }
  }

  Map<String, Object> reverse(UUID original, AccountingDtos.Reversal r) {
    lock();
    String payload =
        payload("REVERSAL", original, r.periodId(), r.entryDate(), r.reason(), r.reference());
    var replay = retry("journals", r.journalId(), payload);
    if (replay != null) return replay;
    var old = one("journals", original);
    if (!"POSTED".equals(old.get("status")) || old.get("reverses_id") != null)
      throw conflict("Only an original posted journal can be reversed");
    if (r.entryDate().isBefore(day(old.get("entry_date"))))
      throw new IllegalArgumentException("Reversal cannot predate the original journal");
    if (db.queryForObject(
            "select count(*) from journals where reversal_claim=?", Integer.class, original)
        > 0) throw conflict("A draft or posted reversal already exists");
    var lines =
        db
            .queryForList(
                "select * from journal_lines where journal_id=? order by position", original)
            .stream()
            .map(this::normalized)
            .map(
                l ->
                    new AccountingDtos.Line(
                        (UUID) l.get("account_id"),
                        (BigDecimal) l.get("credit"),
                        (BigDecimal) l.get("debit"),
                        (String) l.get("narration")))
            .toList();
    var input =
        new AccountingDtos.Journal(
            r.journalId(),
            r.periodId(),
            (UUID) old.get("branch_id"),
            r.entryDate(),
            r.reason(),
            r.reference(),
            lines);
    return insert(input, original, payload);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Map<String, Object> trial(LocalDate asOf, UUID branch) {
    String sql =
        "select a.id,a.code,a.name,a.type,sum(l.debit) as debit,sum(l.credit) as credit from"
            + " journal_lines l join journals j on j.id=l.journal_id join ledger_accounts a on"
            + " a.id=l.account_id where j.status='POSTED' and j.entry_date<=?"
            + (branch == null ? "" : " and j.branch_id=?")
            + " group by a.id,a.code,a.name,a.type order by a.code";
    var args = new ArrayList<Object>();
    args.add(asOf);
    if (branch != null) args.add(branch);
    var rows = new ArrayList<Map<String, Object>>();
    BigDecimal debit = BigDecimal.ZERO,
        credit = BigDecimal.ZERO,
        movementDebit = BigDecimal.ZERO,
        movementCredit = BigDecimal.ZERO;
    for (var raw : db.queryForList(sql, args.toArray())) {
      var row = normalized(raw);
      BigDecimal d = (BigDecimal) row.get("debit"),
          c = (BigDecimal) row.get("credit"),
          net = d.subtract(c);
      BigDecimal dbal = net.max(BigDecimal.ZERO), cbal = net.negate().max(BigDecimal.ZERO);
      row.put("debit_balance", dbal);
      row.put("credit_balance", cbal);
      rows.add(row);
      debit = debit.add(dbal);
      credit = credit.add(cbal);
      movementDebit = movementDebit.add(d);
      movementCredit = movementCredit.add(c);
    }
    return Map.of(
        "asOf",
        asOf,
        "currency",
        "INR",
        "accounts",
        rows,
        "debitBalance",
        debit,
        "creditBalance",
        credit,
        "movementDebit",
        movementDebit,
        "movementCredit",
        movementCredit,
        "balanced",
        debit.compareTo(credit) == 0);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Map<String, Object> ledger(
      UUID id, LocalDate from, LocalDate to, UUID branch, int page, int size) {
    if (from.isAfter(to)) throw new IllegalArgumentException("Ledger start must not follow end");
    one("ledger_accounts", id);
    String where =
        " from journal_lines l join journals j on j.id=l.journal_id where j.status='POSTED' and"
            + " l.account_id=?"
            + (branch == null ? "" : " and j.branch_id=?");
    var args = new ArrayList<Object>();
    args.add(id);
    if (branch != null) args.add(branch);
    args.add(from);
    BigDecimal opening =
        db.queryForObject(
            "select coalesce(sum(l.debit-l.credit),0)" + where + " and j.entry_date<?",
            BigDecimal.class,
            args.toArray());
    args = new ArrayList<>();
    args.add(opening);
    args.add(id);
    if (branch != null) args.add(branch);
    args.add(from);
    args.add(to);
    args.add(size);
    args.add((long) page * size);
    var rows =
        db
            .queryForList(
                "select j.id as"
                    + " journal_id,j.number,j.entry_date,l.position,l.debit,l.credit,l.narration,?+sum(l.debit-l.credit)"
                    + " over(order by j.entry_date,j.id,l.position rows unbounded preceding) as"
                    + " running_balance"
                    + where
                    + " and j.entry_date>=? and j.entry_date<=? order by"
                    + " j.entry_date,j.id,l.position limit ? offset ?",
                args.toArray())
            .stream()
            .map(this::normalized)
            .toList();
    var closingArgs = new ArrayList<Object>();
    closingArgs.add(id);
    if (branch != null) closingArgs.add(branch);
    closingArgs.add(to);
    BigDecimal closing =
        db.queryForObject(
            "select coalesce(sum(l.debit-l.credit),0)" + where + " and j.entry_date<=?",
            BigDecimal.class,
            closingArgs.toArray());
    return Map.of(
        "accountId",
        id,
        "from",
        from,
        "to",
        to,
        "currency",
        "INR",
        "openingBalance",
        opening,
        "closingBalance",
        closing,
        "entries",
        rows,
        "page",
        page,
        "size",
        size);
  }
}
