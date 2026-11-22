package com.yfctransport.fleet;

import com.yfctransport.audit.AuditTrail;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class FleetMaintenanceService {
  enum Category {
    PREVENTIVE_SERVICE,
    TYRE,
    BRAKE_CLUTCH,
    ENGINE,
    BODY_HYDRAULIC,
    INSPECTION,
    OTHER
  }

  record CreateLog(
      @NotNull UUID truckId,
      @NotNull Category category,
      @NotNull LocalDate serviceDate,
      @NotNull @Min(0) @Max(10_000_000) Integer odometerKm,
      LocalDate nextDueDate,
      @Min(0) @Max(10_000_000) Integer nextDueOdometerKm,
      @NotBlank @Size(max = 160) String vendorName,
      @Size(max = 120) String invoiceReference,
      @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal costInr,
      @Size(max = 1000) String notes) {}

  private final JdbcTemplate db;
  private final AuditTrail audit;
  private final Clock clock;

  FleetMaintenanceService(JdbcTemplate db, AuditTrail audit, Clock clock) {
    this.db = db;
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

  Map<String, Object> record(CreateLog r) {
    var trucks =
        db.queryForList(
            "select id, registration, branch_id, current_odometer_km from trucks where id=? for update",
            r.truckId());
    if (trucks.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Truck not found");
    var truck = norm(trucks.getFirst());
    if (r.nextDueOdometerKm() != null && r.nextDueOdometerKm() < r.odometerKm()) {
      throw new IllegalArgumentException(
          "Next due odometer reading must be greater than or equal to current odometer");
    }
    if (r.nextDueDate() != null && r.nextDueDate().isBefore(r.serviceDate())) {
      throw new IllegalArgumentException("Next due date cannot be before service date");
    }
    UUID id = UUID.randomUUID();
    UUID branchId = (UUID) truck.get("branch_id");
    db.update(
        "insert into fleet_maintenance_logs(id,truck_id,branch_id,category,service_date,"
            + " odometer_km,next_due_date,next_due_odometer_km,vendor_name,invoice_reference,"
            + " cost_inr,notes,recorded_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        r.truckId(),
        branchId,
        r.category().name(),
        r.serviceDate(),
        r.odometerKm(),
        r.nextDueDate(),
        r.nextDueOdometerKm(),
        r.vendorName().strip(),
        r.invoiceReference() == null || r.invoiceReference().isBlank()
            ? null
            : r.invoiceReference().strip(),
        r.costInr().setScale(2, RoundingMode.HALF_UP),
        r.notes() == null || r.notes().isBlank() ? null : r.notes().strip(),
        actor());

    int currentOdo =
        truck.get("current_odometer_km") instanceof Number n ? n.intValue() : 0;
    if (r.odometerKm() > currentOdo) {
      db.update("update trucks set current_odometer_km=? where id=?", r.odometerKm(), r.truckId());
    }
    audit.record(
        "FLEET_MAINTENANCE_RECORDED",
        "TRUCK",
        r.truckId(),
        r.category().name() + " ₹" + r.costInr().setScale(2, RoundingMode.HALF_UP) + " @ " + r.odometerKm() + " km");
    return get(id);
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(UUID id) {
    var rows =
        db.queryForList(
            "select m.*, t.registration as truck_registration, t.current_odometer_km,"
                + " b.name as branch_name"
                + " from fleet_maintenance_logs m"
                + " join trucks t on t.id = m.truck_id"
                + " left join branches b on b.id = m.branch_id"
                + " where m.id=?",
            id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance log not found");
    return norm(rows.getFirst());
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(
      int page, int size, UUID truckId, UUID branchId, Category category) {
    var where = new StringBuilder(" where 1=1");
    var args = new ArrayList<Object>();
    if (truckId != null) {
      where.append(" and m.truck_id=?");
      args.add(truckId);
    }
    if (branchId != null) {
      where.append(" and m.branch_id=?");
      args.add(branchId);
    }
    if (category != null) {
      where.append(" and m.category=?");
      args.add(category.name());
    }
    args.add(size);
    args.add((long) page * size);
    return db
        .queryForList(
            "select m.*, t.registration as truck_registration, t.current_odometer_km,"
                + " b.name as branch_name"
                + " from fleet_maintenance_logs m"
                + " join trucks t on t.id = m.truck_id"
                + " left join branches b on b.id = m.branch_id"
                + where
                + " order by m.service_date desc, m.created_at desc limit ? offset ?",
            args.toArray())
        .stream()
        .map(this::norm)
        .toList();
  }

  /**
   * Lists the latest maintenance schedule per (truck, category) where either next_due_date falls
   * within the next {@code days} (or is overdue) OR current_odometer_km is within {@code kmMargin}
   * of next_due_odometer_km.
   */
  @Transactional(readOnly = true)
  List<Map<String, Object>> dueAlerts(int days, int kmMargin, UUID branchId) {
    LocalDate today = LocalDate.now(clock);
    LocalDate cutoff = today.plusDays(days);
    var args = new ArrayList<Object>();
    String branchClause = "";
    if (branchId != null) {
      branchClause = " and t.branch_id=?";
      args.add(branchId);
    }
    var logs =
        db.queryForList(
                "select m.id, m.truck_id, t.registration as truck_registration,"
                    + " t.operational_status, t.current_odometer_km, b.name as branch_name,"
                    + " m.category, m.service_date, m.odometer_km, m.next_due_date,"
                    + " m.next_due_odometer_km, m.vendor_name"
                    + " from fleet_maintenance_logs m"
                    + " join trucks t on t.id = m.truck_id"
                    + " left join branches b on b.id = t.branch_id"
                    + " where (m.next_due_date is not null or m.next_due_odometer_km is not null)"
                    + branchClause
                    + " order by m.service_date desc, m.created_at desc",
                args.toArray())
            .stream()
            .map(this::norm)
            .toList();

    // Keep only the latest log per (truck_id, category) so older completed schedules don't re-alert
    var seen = new HashSet<String>();
    var alerts = new ArrayList<Map<String, Object>>();
    for (var log : logs) {
      String key = log.get("truck_id") + ":" + log.get("category");
      if (!seen.add(key)) continue;
      LocalDate nextDate = toDate(log.get("next_due_date"));
      Integer nextKm =
          log.get("next_due_odometer_km") instanceof Number n ? n.intValue() : null;
      int currentKm =
          log.get("current_odometer_km") instanceof Number n ? n.intValue() : 0;

      boolean dateOverdue = nextDate != null && nextDate.isBefore(today);
      boolean dateDueSoon = nextDate != null && !nextDate.isAfter(cutoff);
      boolean kmOverdue = nextKm != null && currentKm >= nextKm;
      boolean kmDueSoon = nextKm != null && currentKm + kmMargin >= nextKm;

      if (dateDueSoon || kmDueSoon) {
        log.put("overdue", dateOverdue || kmOverdue);
        log.put("km_remaining", nextKm == null ? null : nextKm - currentKm);
        alerts.add(log);
      }
    }
    return alerts;
  }

  private LocalDate toDate(Object v) {
    if (v == null) return null;
    if (v instanceof LocalDate d) return d;
    if (v instanceof java.sql.Date d) return d.toLocalDate();
    return LocalDate.parse(v.toString());
  }
}
