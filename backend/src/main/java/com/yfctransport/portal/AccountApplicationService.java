package com.yfctransport.portal;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import com.yfctransport.customers.CustomerOpening;
import com.yfctransport.fleet.DriverOpening;
import com.yfctransport.identity.ExternalAccounts;
import com.yfctransport.suppliers.SupplierOpening;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
class AccountApplicationService {
  private final JdbcTemplate db;
  private final ExternalAccounts accounts;
  private final CustomerOpening customers;
  private final SupplierOpening suppliers;
  private final DriverOpening drivers;
  private final BranchRegistry branches;
  private final AuditTrail audit;
  private final ObjectMapper json;

  AccountApplicationService(
      JdbcTemplate db,
      ExternalAccounts accounts,
      CustomerOpening customers,
      SupplierOpening suppliers,
      DriverOpening drivers,
      BranchRegistry branches,
      AuditTrail audit,
      ObjectMapper json) {
    this.db = db;
    this.accounts = accounts;
    this.customers = customers;
    this.suppliers = suppliers;
    this.drivers = drivers;
    this.branches = branches;
    this.audit = audit;
    this.json = json;
  }

  private void lock() {
    db.queryForObject("select id from portal_guard where id=1 for update", Integer.class);
    accounts.lock();
  }

  private Map<String, Object> norm(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = norm(row);
    result.remove("password_hash");
    result.remove("create_payload");
    return result;
  }

  private Map<String, Object> one(UUID id) {
    var rows = db.queryForList("select * from account_applications where id=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account application not found");
    return norm(rows.getFirst());
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.strip();
  }

  Map<String, Object> submit(AccountApplicationController.Input r) {
    lock();
    String role = r.role();
    String username = r.username().toLowerCase(Locale.ROOT);
    String name = r.name().strip();
    String mobile = r.mobile().strip();
    String gstin = blankToNull(r.gstin());
    if (gstin != null) gstin = gstin.toUpperCase(Locale.ROOT);
    String address = blankToNull(r.address());
    String email = blankToNull(r.email());
    String license = blankToNull(r.licenseNumber());
    if (license != null) license = license.toUpperCase(Locale.ROOT);
    LocalDate licenseExpiry = r.licenseExpiry();
    String emergencyContact = blankToNull(r.emergencyContact());
    String emergencyMobile = blankToNull(r.emergencyMobile());
    if (role.equals("CUSTOMER")) {
      license = null;
      licenseExpiry = null;
      emergencyContact = null;
      emergencyMobile = null;
      email = null;
    } else if (role.equals("SUPPLIER")) {
      if (gstin == null || address == null)
        throw new IllegalArgumentException("A supplier application needs a GSTIN and an address");
      if (email != null && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
        throw new IllegalArgumentException("Enter a valid email address");
      license = null;
      licenseExpiry = null;
      emergencyContact = null;
      emergencyMobile = null;
    } else if (role.equals("DRIVER")) {
      if (license == null
          || licenseExpiry == null
          || emergencyContact == null
          || emergencyMobile == null)
        throw new IllegalArgumentException(
            "A driver application needs a licence number, expiry and an emergency contact");
      gstin = null;
      address = null;
      email = null;
    } else throw new IllegalArgumentException("Choose customer, supplier or driver");
    var payloadItems = new ArrayList<>();
    payloadItems.add(role);
    payloadItems.add(username);
    payloadItems.add(name);
    payloadItems.add(mobile);
    payloadItems.add(gstin);
    payloadItems.add(address);
    payloadItems.add(email);
    payloadItems.add(license);
    payloadItems.add(licenseExpiry);
    payloadItems.add(emergencyContact);
    payloadItems.add(emergencyMobile);
    String payload = json.writeValueAsString(payloadItems);
    var existing = db.queryForList("select * from account_applications where id=?", r.applicationId());
    if (!existing.isEmpty()) {
      var old = norm(existing.getFirst());
      if (!payload.equals(old.get("create_payload"))
          || !accounts.passwordMatches(r.password(), (String) old.get("password_hash")))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Application ID used with different details");
      var result = view(old);
      result.put("replayed", true);
      return result;
    }
    accounts.validatePassword(r.password());
    if (!username.matches("[a-z0-9][a-z0-9._-]{2,79}"))
      throw new IllegalArgumentException("Invalid username");
    if (accounts.usernameTaken(username)
        || db.queryForObject(
                "select count(*) from account_applications where username=?",
                Integer.class,
                username)
            > 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
    if (gstin != null
        && (db.queryForObject("select count(*) from suppliers where gstin=?", Integer.class, gstin)
                > 0
            || db.queryForObject(
                    "select count(*) from account_applications where gstin=? and status='PENDING'",
                    Integer.class,
                    gstin)
                > 0))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This GSTIN already has a supplier or a pending application");
    if (license != null
        && (db.queryForObject(
                    "select count(*) from drivers where license_number=?", Integer.class, license)
                > 0
            || db.queryForObject(
                    "select count(*) from account_applications where license_number=? and"
                        + " status='PENDING'",
                    Integer.class,
                    license)
                > 0))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This licence number already has a driver or a pending application");
    db.update(
        "insert into account_applications(id,role,username,requested_username,display_name,mobile,"
            + " password_hash,gstin,address,email,license_number,license_expiry,emergency_contact,emergency_mobile,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        r.applicationId(),
        role,
        username,
        username,
        name,
        mobile,
        accounts.encode(r.password()),
        gstin,
        address,
        email,
        license,
        licenseExpiry,
        emergencyContact,
        emergencyMobile,
        payload);
    audit.record(
        "ACCOUNT_APPLICATION_SUBMITTED",
        "ACCOUNT_APPLICATION",
        r.applicationId(),
        "Role " + role + " requested by " + username);
    return view(one(r.applicationId()));
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(String status, int page, int size) {
    String sql =
        "select * from account_applications"
            + (status == null ? "" : " where status=?")
            + " order by created_at,id limit ? offset ?";
    var args = new ArrayList<Object>();
    if (status != null) {
      if (!List.of("PENDING", "APPROVED", "REJECTED").contains(status))
        throw new IllegalArgumentException("Unknown application status");
      args.add(status);
    }
    args.add(size);
    args.add((long) page * size);
    return db.queryForList(sql, args.toArray()).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(UUID id) {
    return view(one(id));
  }

  Map<String, Object> decide(UUID id, AccountApplicationController.Decision r, boolean approve) {
    lock();
    var row = one(id);
    String reason = r.reason().strip();
    String actor =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();
    UUID branch = r.branchId();
    if (row.get("decision_event_id") != null) {
      if (!r.eventId().equals(row.get("decision_event_id"))
          || !reason.equals(row.get("decision_reason"))
          || !actor.equals(row.get("decided_by"))
          || (approve && !"APPROVED".equals(row.get("status")))
          || (!approve && !"REJECTED".equals(row.get("status")))
          || (approve && branch != null && !branch.equals(row.get("branch_id")))
          || (approve && branch == null && row.get("branch_id") != null))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Decision ID used with different details");
      var result = view(row);
      result.put("replayed", true);
      return result;
    }
    if (!"PENDING".equals(row.get("status")))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This application has already been decided");
    UUID record = null;
    UUID account = null;
    if (approve) {
      String role = (String) row.get("role");
      if (role.equals("DRIVER")) {
        if (branch == null)
          throw new IllegalArgumentException("Choose the driver's branch before approving");
        branches.requireActive(branch);
        record =
            drivers.open(
                branch,
                (String) row.get("display_name"),
                (String) row.get("mobile"),
                (String) row.get("license_number"),
                day(row.get("license_expiry")),
                (String) row.get("emergency_contact"),
                (String) row.get("emergency_mobile"));
      } else if (branch != null)
        throw new IllegalArgumentException("A branch is recorded only for a driver");
      else if (role.equals("CUSTOMER"))
        record =
            customers.open(
                (String) row.get("display_name"),
                (String) row.get("mobile"),
                (String) row.get("gstin"),
                (String) row.get("address"));
      else
        record =
            suppliers.open(
                (String) row.get("display_name"),
                (String) row.get("gstin"),
                (String) row.get("mobile"),
                (String) row.get("email"),
                (String) row.get("address"));
      account =
          accounts.openHashed(
              (String) row.get("username"),
              (String) row.get("display_name"),
              (String) row.get("password_hash"),
              role);
      db.update(
          "insert into account_ownership(account_id,role,record_id) values(?,?,?)",
          account,
          role,
          record);
    } else if (branch != null)
      throw new IllegalArgumentException("A branch is recorded only when approving a driver");
    String username = (String) row.get("username");
    if (!approve) {
      String suffix = "-" + id.toString().substring(0, 8);
      String base = username;
      if (base.length() + suffix.length() > 80) base = base.substring(0, 80 - suffix.length());
      username = base + suffix;
    }
    db.update(
        "update account_applications set status=?,record_id=?,account_id=?,branch_id=?,username=?,"
            + " decision_event_id=?,decision_reason=?,decided_by=?,decided_at=current_timestamp where"
            + " id=?",
        approve ? "APPROVED" : "REJECTED",
        record,
        account,
        approve ? branch : null,
        username,
        r.eventId(),
        reason,
        actor,
        id);
    audit.record(
        approve ? "ACCOUNT_APPLICATION_APPROVED" : "ACCOUNT_APPLICATION_REJECTED",
        "ACCOUNT_APPLICATION",
        id,
        "Event=" + r.eventId());
    return view(one(id));
  }

  private LocalDate day(Object value) {
    if (value instanceof LocalDate date) return date;
    if (value instanceof java.sql.Date date) return date.toLocalDate();
    return ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate();
  }
}
