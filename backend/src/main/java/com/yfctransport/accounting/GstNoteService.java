package com.yfctransport.accounting;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * CGST Section 34 compliant Credit Notes and Debit Notes linked to taxed invoices / supplier bills,
 * with per-state FY sequential numbering (max 16 chars) and maker-checker approval.
 */
@Service
@Transactional
class GstNoteService {
  enum NoteType {
    CREDIT_NOTE,
    DEBIT_NOTE
  }

  enum ReasonCode {
    SALES_RETURN,
    RATE_DIFFERENCE,
    WEIGHT_SHORTAGE,
    QUALITY_DEDUCTION,
    POST_SALE_DISCOUNT,
    ADDITIONAL_CHARGE
  }

  record CreateNote(
      @NotNull UUID originalPreparationId,
      @NotNull NoteType noteType,
      @NotNull LocalDate noteDate,
      @NotNull ReasonCode reasonCode,
      @NotBlank @Size(max = 1000) String reasonDetail,
      @NotBlank @Pattern(regexp = "[0-9]{4,8}") String hsnOrSac,
      @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal taxableAmount,
      @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
          BigDecimal gstRatePercent) {}

  record Decision(@NotBlank @Size(max = 1000) String reason) {}

  private final JdbcTemplate db;
  private final BranchRegistry branches;
  private final AuditTrail audit;
  private final Clock clock;

  GstNoteService(JdbcTemplate db, BranchRegistry branches, AuditTrail audit, Clock clock) {
    this.db = db;
    this.branches = branches;
    this.audit = audit;
    this.clock = clock;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private Map<String, Object> norm(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  Map<String, Object> create(CreateNote r) {
    if (r.noteDate().isAfter(LocalDate.now(clock))) {
      throw new IllegalArgumentException("Note date cannot be in the future");
    }
    var rows =
        db.queryForList(
            "select p.id, p.kind, p.branch_id, p.party_id, p.party_name, p.status, p.prepared_on,"
                + " t.supply, t.invoice_number, t.taxable_amount as orig_taxable"
                + " from billing_preparations p"
                + " left join billing_tax_assessments t on t.preparation_id = p.id"
                + " where p.id=?",
            r.originalPreparationId());
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Original billing preparation not found");
    }
    var orig = norm(rows.getFirst());
    if (!"READY".equals(orig.get("status"))) {
      throw conflict("Credit/Debit notes can only be issued against a READY bill");
    }
    String supply = (String) orig.get("supply");
    if (supply == null) {
      throw conflict("Assess GST on the original bill before issuing a Credit or Debit Note");
    }
    UUID branchId = (UUID) orig.get("branch_id");
    var branchProfile = branches.profile(branchId);
    String stateCode =
        branchProfile.stateCode() != null && !branchProfile.stateCode().isBlank()
            ? branchProfile.stateCode()
            : "00";
    int fyStart = BillingPreparationService.indianFinancialYearStart(r.noteDate());
    String fyCode = BillingPreparationService.indianFinancialYearCode(fyStart);

    // Lock and increment per-state FY note counter
    db.queryForObject("select id from accounting_guard where id=1 for update", Integer.class);
    var counters =
        db.queryForList(
            "select last_number from state_note_counters where note_type=? and state_code=? and fy_start=? for update",
            r.noteType().name(),
            stateCode,
            fyStart);
    int next;
    if (counters.isEmpty()) {
      next = 1;
      db.update(
          "insert into state_note_counters(note_type,state_code,fy_start,last_number) values(?,?,?,?)",
          r.noteType().name(),
          stateCode,
          fyStart,
          next);
    } else {
      next = ((Number) norm(counters.getFirst()).get("last_number")).intValue() + 1;
      db.update(
          "update state_note_counters set last_number=? where note_type=? and state_code=? and fy_start=?",
          next,
          r.noteType().name(),
          stateCode,
          fyStart);
    }
    String prefix = r.noteType() == NoteType.CREDIT_NOTE ? "CN" : "DN";
    // 16-char CGST Rule 46/53 compliant series: e.g. CN-2627-08-00001
    String noteNumber = prefix + "-" + fyCode + "-" + stateCode + "-" + String.format("%05d", next);

    BigDecimal taxable = r.taxableAmount().setScale(2, RoundingMode.HALF_UP);
    BigDecimal rate = r.gstRatePercent().setScale(2, RoundingMode.HALF_UP);
    BigDecimal taxTotal =
        taxable.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    BigDecimal cgst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal sgst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal igst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    if ("INTRA_STATE".equals(supply)) {
      cgst = taxTotal.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
      sgst = taxTotal.subtract(cgst);
    } else {
      igst = taxTotal;
    }
    BigDecimal total = taxable.add(taxTotal);
    UUID id = UUID.randomUUID();

    db.update(
        "insert into gst_adjustment_notes(id,note_type,note_number,original_preparation_id,"
            + " original_invoice_number,branch_id,party_id,party_name,kind,note_date,reason_code,"
            + " reason_detail,supply,hsn_or_sac,taxable_amount,gst_rate_percent,cgst_amount,"
            + " sgst_amount,igst_amount,tax_total,total_amount,status,created_by)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?)",
        id,
        r.noteType().name(),
        noteNumber,
        r.originalPreparationId(),
        orig.get("invoice_number"),
        branchId,
        orig.get("party_id"),
        orig.get("party_name"),
        orig.get("kind"),
        r.noteDate(),
        r.reasonCode().name(),
        r.reasonDetail().strip(),
        supply,
        r.hsnOrSac().strip(),
        taxable,
        rate,
        cgst,
        sgst,
        igst,
        taxTotal,
        total,
        actor());
    audit.record(
        "GST_NOTE_CREATED",
        "GST_NOTE",
        id,
        noteNumber + " (" + r.noteType() + ") against " + orig.get("invoice_number"));
    return get(id);
  }

  Map<String, Object> decide(UUID id, Decision r, boolean approve) {
    var rows =
        db.queryForList("select * from gst_adjustment_notes where id=? for update", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GST note not found");
    var note = norm(rows.getFirst());
    if (!"DRAFT".equals(note.get("status"))) {
      throw conflict("Only DRAFT Credit/Debit notes can be approved or cancelled");
    }
    String currentActor = actor();
    if (approve && currentActor.equals(note.get("created_by"))) {
      throw conflict("A different accounts user or administrator must approve this Credit/Debit note");
    }
    String nextStatus = approve ? "APPROVED" : "CANCELLED";
    db.update(
        "update gst_adjustment_notes set status=?, approved_by=?, decision_reason=?, approved_at=? where id=?",
        nextStatus,
        currentActor,
        r.reason().strip(),
        Timestamp.from(Instant.now(clock)),
        id);
    audit.record(
        approve ? "GST_NOTE_APPROVED" : "GST_NOTE_CANCELLED",
        "GST_NOTE",
        id,
        note.get("note_number") + " -> " + nextStatus);
    return get(id);
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(UUID id) {
    var rows =
        db.queryForList(
            "select n.*, p.prepared_on as original_invoice_date"
                + " from gst_adjustment_notes n"
                + " join billing_preparations p on p.id = n.original_preparation_id"
                + " where n.id=?",
            id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GST note not found");
    var result = norm(rows.getFirst());
    result.put("currency", "INR");

    var branchProfile = branches.profile((UUID) result.get("branch_id"));
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

    String partyTable = "SALES_ORDER".equals(result.get("kind")) ? "customers" : "suppliers";
    var partyRows =
        db.queryForList(
            "select gstin, address, mobile from " + partyTable + " where id=?",
            result.get("party_id"));
    if (!partyRows.isEmpty()) {
      var p = norm(partyRows.getFirst());
      String gstin = (String) p.get("gstin");
      result.put("party_gstin", gstin);
      result.put("party_address", p.get("address"));
      result.put("party_mobile", p.get("mobile"));
      if (gstin != null && gstin.length() >= 2) {
        result.put("party_state_code", gstin.substring(0, 2));
      }
    }
    return result;
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(
      int page,
      int size,
      NoteType noteType,
      String status,
      UUID branchId,
      UUID originalPreparationId,
      String q) {
    var where = new StringBuilder(" where 1=1");
    var args = new ArrayList<Object>();
    if (noteType != null) {
      where.append(" and n.note_type=?");
      args.add(noteType.name());
    }
    if (status != null && !status.isBlank()) {
      where.append(" and n.status=?");
      args.add(status.strip().toUpperCase(Locale.ROOT));
    }
    if (branchId != null) {
      where.append(" and n.branch_id=?");
      args.add(branchId);
    }
    if (originalPreparationId != null) {
      where.append(" and n.original_preparation_id=?");
      args.add(originalPreparationId);
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
      where.append(
          " and (lower(coalesce(n.note_number,'')) like ?"
              + " or lower(coalesce(n.original_invoice_number,'')) like ?"
              + " or lower(coalesce(n.party_name,'')) like ?)");
      args.add(like);
      args.add(like);
      args.add(like);
    }
    args.add(size);
    args.add((long) page * size);
    return db
        .queryForList(
            "select n.*, b.code as branch_code, b.name as branch_name"
                + " from gst_adjustment_notes n"
                + " join branches b on b.id = n.branch_id"
                + where
                + " order by n.note_date desc, n.created_at desc limit ? offset ?",
            args.toArray())
        .stream()
        .map(this::norm)
        .toList();
  }
}
