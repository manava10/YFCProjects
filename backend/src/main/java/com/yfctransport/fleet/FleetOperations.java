package com.yfctransport.fleet;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class FleetOperations implements FleetAssignments {
  private final JdbcTemplate db;
  private final BranchRegistry branches;
  private final AuditTrail audit;
  private final Clock clock;

  FleetOperations(JdbcTemplate db, BranchRegistry branches, AuditTrail audit, Clock clock) {
    this.db = db;
    this.branches = branches;
    this.audit = audit;
    this.clock = clock;
  }

  enum OperationalStatus {
    ACTIVE,
    INACTIVE,
    MAINTENANCE
  }

  record Compliance(
      @NotNull UUID branchId,
      @NotBlank @Size(max = 160) String ownerName,
      @NotBlank @Size(max = 80) String truckType,
      @NotBlank @Size(max = 80) String rcNumber,
      @NotBlank @Size(max = 80) String insuranceNumber,
      @NotNull LocalDate insuranceExpiry,
      @NotNull LocalDate fitnessExpiry,
      @NotNull LocalDate permitExpiry,
      @NotNull LocalDate pollutionExpiry,
      @NotNull OperationalStatus status) {}

  record DriverInput(
      @NotNull UUID branchId,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}") String mobile,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{5,40}") String licenseNumber,
      @NotNull LocalDate licenseExpiry,
      @NotBlank @Size(max = 160) String emergencyContact,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}") String emergencyMobile,
      @NotNull Boolean active) {}

  private Map<String, Object> one(String table, UUID id, boolean lock) {
    var rows =
        db.queryForList("select * from " + table + " where id=?" + (lock ? " for update" : ""), id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fleet record not found");
    return normalize(rows.getFirst());
  }

  private Map<String, Object> normalize(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  Map<String, Object> compliance(UUID id, Compliance r) {
    var t = one("trucks", id, true);
    branches.requireActive(r.branchId());
    if (!r.branchId().equals(t.get("branch_id"))
        && db.queryForObject(
                "select count(*) from fleet_assignments where truck_id=?", Integer.class, id)
            > 0) throw conflict("Cannot change branch while truck has an active trip");
    db.update(
        "update trucks set"
            + " branch_id=?,owner_name=?,truck_type=?,rc_number=?,insurance_number=?,insurance_expiry=?,fitness_expiry=?,permit_expiry=?,pollution_expiry=?,operational_status=?"
            + " where id=?",
        r.branchId(),
        r.ownerName().strip(),
        r.truckType().strip(),
        r.rcNumber().strip(),
        r.insuranceNumber().strip(),
        r.insuranceExpiry(),
        r.fitnessExpiry(),
        r.permitExpiry(),
        r.pollutionExpiry(),
        r.status().name(),
        id);
    audit.record("TRUCK_COMPLIANCE_UPDATED", "TRUCK", id, "Operational profile updated");
    return one("trucks", id, false);
  }

  Map<String, Object> driver(UUID id, DriverInput r) {
    branches.requireActive(r.branchId());
    if (id == null) {
      id = UUID.randomUUID();
      db.update(
          "insert into"
              + " drivers(id,branch_id,name,mobile,license_number,license_expiry,emergency_contact,emergency_mobile,active)"
              + " values(?,?,?,?,?,?,?,?,?)",
          id,
          r.branchId(),
          r.name().strip(),
          r.mobile().strip(),
          r.licenseNumber().toUpperCase(Locale.ROOT),
          r.licenseExpiry(),
          r.emergencyContact().strip(),
          r.emergencyMobile().strip(),
          r.active());
    } else {
      var old = one("drivers", id, true);
      if (!r.branchId().equals(old.get("branch_id"))
          && db.queryForObject(
                  "select count(*) from fleet_assignments where driver_id=?", Integer.class, id)
              > 0) throw conflict("Cannot change branch while driver has an active trip");
      db.update(
          "update drivers set"
              + " branch_id=?,name=?,mobile=?,license_number=?,license_expiry=?,emergency_contact=?,emergency_mobile=?,active=?"
              + " where id=?",
          r.branchId(),
          r.name().strip(),
          r.mobile().strip(),
          r.licenseNumber().toUpperCase(Locale.ROOT),
          r.licenseExpiry(),
          r.emergencyContact().strip(),
          r.emergencyMobile().strip(),
          r.active(),
          id);
    }
    audit.record("DRIVER_SAVED", "DRIVER", id, "Driver profile saved");
    return one("drivers", id, false);
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(String table, UUID id) {
    var result = one(table, id, false);
    result.put(
        "active_trip",
        db.queryForList(
            "select trip_id from fleet_assignments where "
                + (table.equals("trucks") ? "truck_id" : "driver_id")
                + "=?",
            id));
    return result;
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(String table, int page, int size) {
    return db
        .queryForList(
            "select * from " + table + " order by id limit ? offset ?", size, (long) page * size)
        .stream()
        .map(this::normalize)
        .toList();
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> alerts(UUID branch, int days, int page, int size) {
    LocalDate until = LocalDate.now(clock).plusDays(days);
    String sql =
        "select"
            + " id,registration,branch_id,insurance_expiry,fitness_expiry,permit_expiry,pollution_expiry"
            + " from trucks where (branch_id is null or insurance_expiry is null or fitness_expiry"
            + " is null or permit_expiry is null or pollution_expiry is null or insurance_expiry<=?"
            + " or fitness_expiry<=? or permit_expiry<=? or pollution_expiry<=?)";
    var args = new ArrayList<Object>(List.of(until, until, until, until));
    if (branch != null) {
      sql += " and branch_id=?";
      args.add(branch);
    }
    sql += " order by registration,id limit ? offset ?";
    args.add(size);
    args.add((long) page * size);
    return db.queryForList(sql, args.toArray()).stream().map(this::normalize).toList();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Vehicle requireTruck(UUID id, UUID branch, LocalDate date) {
    var t = one("trucks", id, true);
    if (!branch.equals(t.get("branch_id")) || !"ACTIVE".equals(t.get("operational_status")))
      throw conflict("Truck must be active in the order branch");
    for (String field :
        List.of("insurance_expiry", "fitness_expiry", "permit_expiry", "pollution_expiry")) {
      var expiry = (java.sql.Date) t.get(field);
      if (expiry == null || expiry.toLocalDate().isBefore(date))
        throw conflict("Truck compliance is missing or expired: " + field);
    }
    return new Vehicle(
        (String) t.get("registration"),
        (BigDecimal) t.get("capacity_tonnes"),
        (BigDecimal) t.get("mileage_km_per_litre"));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public String requireDriver(UUID id, UUID branch, LocalDate date) {
    var d = one("drivers", id, true);
    if (!branch.equals(d.get("branch_id")) || !Boolean.TRUE.equals(d.get("active")))
      throw conflict("Driver must be active in the order branch");
    if (((java.sql.Date) d.get("license_expiry")).toLocalDate().isBefore(date))
      throw conflict("Driver license is expired");
    return (String) d.get("name");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void assign(UUID trip, UUID truck, UUID driver) {
    if (db.queryForObject(
            "select count(*) from fleet_assignments where truck_id=? or driver_id=?",
            Integer.class,
            truck,
            driver)
        > 0) throw conflict("Truck or driver already has an active trip");
    db.update(
        "insert into fleet_assignments(truck_id,driver_id,trip_id) values(?,?,?)",
        truck,
        driver,
        trip);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void release(UUID trip) {
    db.update("delete from fleet_assignments where trip_id=?", trip);
  }
}
