package com.yfctransport.portal;

import com.yfctransport.documents.TripDocumentUploader;
import com.yfctransport.identity.ExternalAccounts;
import com.yfctransport.logistics.DriverTripExecution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portal")
@PreAuthorize("hasAnyRole('CUSTOMER','SUPPLIER','DRIVER')")
class PortalController {
  private final JdbcTemplate db;
  private final ExternalAccounts accounts;
  private final DriverTripExecution driverTrips;
  private final TripDocumentUploader tripDocuments;

  PortalController(
      JdbcTemplate db,
      ExternalAccounts accounts,
      DriverTripExecution driverTrips,
      TripDocumentUploader tripDocuments) {
    this.db = db;
    this.accounts = accounts;
    this.driverTrips = driverTrips;
    this.tripDocuments = tripDocuments;
  }

  private record Ownership(String role, UUID recordId) {}

  record DriverEvent(@NotNull UUID eventId) {}

  record DriverLoad(
      @NotNull UUID eventId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal gross,
      @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal tare,
      @NotBlank @Size(max = 100) String slipReference) {}

  record DriverDeliver(
      @NotNull UUID eventId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal gross,
      @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal tare,
      @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal acceptedQuantity,
      @NotBlank @Size(max = 100) String slipReference,
      @NotBlank @Size(max = 160) String receiverName,
      @NotBlank @Size(max = 300) String proofReference,
      @Size(max = 1000) String discrepancyReason) {}

  private Map<String, Object> norm(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private List<Map<String, Object>> rows(String sql, Object... args) {
    return db.queryForList(sql, args).stream().map(this::norm).toList();
  }

  private Ownership resolveOwnership() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    String username = auth.getName();
    UUID accountId = accounts.idOf(username);
    var links =
        db.queryForList("select role,record_id from account_ownership where account_id=?", accountId);
    if (links.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No records are linked to this login");
    var link = norm(links.getFirst());
    String role = (String) link.get("role");
    boolean roleMatches =
        auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    if (!roleMatches)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Account role no longer matches linked portal record");
    return new Ownership(role, (UUID) link.get("record_id"));
  }

  private void requireDriverOwnsTrip(UUID tripId) {
    var owner = resolveOwnership();
    if (!"DRIVER".equals(owner.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only assigned drivers can update trip execution");
    }
    Integer count =
        db.queryForObject(
            "select count(*) from trips where id=? and driver_id=?",
            Integer.class,
            tripId,
            owner.recordId());
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned trip not found");
    }
  }

  @GetMapping
  Map<String, Object> home() {
    var owner = resolveOwnership();
    String role = owner.role();
    UUID record = owner.recordId();
    var result = new LinkedHashMap<String, Object>();
    result.put("role", role);
    result.put("record_id", record);
    if (role.equals("CUSTOMER")) {
      result.put(
          "profile",
          rows("select id,name,mobile,gstin,address from customers where id=?", record).getFirst());
      result.put(
          "orders",
          rows(
              "select i.id,i.material,o.business_type,i.ordered_quantity,o.created_at from"
                  + " sales_order_items i join sales_orders o on o.id=i.order_id where"
                  + " o.customer_id=? order by o.created_at desc,i.position,i.id limit 50",
              record));
      result.put(
          "trips",
          rows(
              "select t.id,t.status,t.planned_date,t.planned_quantity,t.loaded_quantity,"
                  + " t.received_quantity,t.truck_registration,t.eway_bill_number,i.material"
                  + " from trips t join sales_order_items i on i.id=t.order_item_id join"
                  + " sales_orders o on o.id=i.order_id where o.customer_id=? order by"
                  + " t.planned_date desc,t.id limit 50",
              record));
      result.put("bills", bills(record, "SALES_ORDER"));
    } else if (role.equals("SUPPLIER")) {
      result.put(
          "profile",
          rows(
                  "select id,code,name,gstin,mobile,email,address,active from suppliers where id=?",
                  record)
              .getFirst());
      result.put(
          "plants",
          rows(
              "select id,code,name,address,active from supplier_plants where supplier_id=? order by"
                  + " name,id",
              record));
      result.put(
          "purchases",
          rows(
              "select id,material_name,quantity,rate,status,expected_date from purchase_orders where"
                  + " supplier_id=? order by created_at desc,id limit 50",
              record));
      result.put("bills", bills(record, "PURCHASE_RECEIPT"));
    } else {
      result.put(
          "profile",
          rows(
                  "select d.id,d.name,d.mobile,d.license_number,d.license_expiry,d.emergency_contact,"
                      + " d.emergency_mobile,d.active,b.name as branch_name from drivers d join branches"
                      + " b on b.id=d.branch_id where d.id=?",
                  record)
              .getFirst());
      result.put(
          "trips",
          rows(
              "select t.id,t.status,t.source,t.planned_date,t.planned_quantity,t.loaded_quantity,"
                  + " t.received_quantity,t.accepted_quantity,t.loading_slip,t.delivery_slip,"
                  + " t.truck_registration,t.distance_km,t.eway_bill_number,t.eway_bill_valid_until,"
                  + " i.material,o.delivery_site,c.name as customer_name"
                  + " from trips t join sales_order_items i on i.id=t.order_item_id"
                  + " join sales_orders o on o.id=i.order_id"
                  + " join customers c on c.id=o.customer_id"
                  + " where t.driver_id=? order by t.planned_date desc,t.id limit 50",
              record));
    }
    return result;
  }

  /**
   * Returns complete legal GST Tax Invoice / Supplier Bill detail strictly scoped to the logged-in
   * customer or supplier's own READY bill (prevents cross-party IDOR).
   */
  @GetMapping("/bills/{id}")
  Map<String, Object> billDetail(@PathVariable UUID id) {
    var owner = resolveOwnership();
    if (!owner.role().equals("CUSTOMER") && !owner.role().equals("SUPPLIER")) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Bills are only available to customers and suppliers");
    }
    String expectedKind = owner.role().equals("CUSTOMER") ? "SALES_ORDER" : "PURCHASE_RECEIPT";
    var list =
        rows(
            "select p.id,p.kind,p.branch_id,p.party_id,p.party_name,p.prepared_on,p.quantity_basis,"
                + " p.reference,p.subtotal,p.status,p.created_by,"
                + " t.supply,t.invoice_number,t.taxable_amount,t.cgst_amount,t.sgst_amount,"
                + " t.igst_amount,t.tax_total,t.payable_total"
                + " from billing_preparations p left join billing_tax_assessments t on"
                + " t.preparation_id=p.id where p.id=? and p.party_id=? and p.kind=?"
                + " and p.status='READY'",
            id,
            owner.recordId(),
            expectedKind);
    if (list.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
    }
    var bill = new LinkedHashMap<>(list.getFirst());
    bill.put("currency", "INR");
    BigDecimal allocated =
        db.queryForObject(
                "select coalesce(sum(a.amount),0) from invoice_allocations a where"
                    + " a.preparation_id=? and a.status='ACTIVE'",
                BigDecimal.class,
                id)
            .setScale(2, RoundingMode.HALF_UP);
    bill.put("allocated_amount", allocated);
    if (bill.get("payable_total") instanceof BigDecimal payable) {
      BigDecimal scaledPayable = payable.setScale(2, RoundingMode.HALF_UP);
      BigDecimal outstanding = scaledPayable.subtract(allocated);
      bill.put("payable_total", scaledPayable);
      bill.put("outstanding_amount", outstanding);
      bill.put(
          "payment_status",
          outstanding.signum() == 0
              ? "PAID"
              : allocated.signum() > 0 ? "PARTIALLY_PAID" : "UNPAID");
    }
    bill.put(
        "lines",
        rows(
            "select position,material,quantity,loaded_quantity,received_quantity,accepted_quantity,"
                + " material_rate,transport_rate,material_amount,transport_amount"
                + " from billing_preparation_lines where preparation_id=? order by position",
            id));
    bill.put(
        "tax_lines",
        rows(
            "select position,component,hsn_or_sac,rate_percent,taxable_amount,cgst_amount,"
                + " sgst_amount,igst_amount from billing_tax_lines where preparation_id=?"
                + " order by position,component",
            id));
    UUID branchId = (UUID) bill.get("branch_id");
    if (branchId != null) {
      var branchRows =
          rows(
              "select id,code,name,address,city,state,pincode,gstin,state_code,bank_name,"
                  + " bank_account,bank_ifsc from branches where id=?",
              branchId);
      if (!branchRows.isEmpty()) bill.put("branch", branchRows.getFirst());
    }
    String partyTable = owner.role().equals("CUSTOMER") ? "customers" : "suppliers";
    var partyRows =
        rows("select gstin,address,mobile from " + partyTable + " where id=?", owner.recordId());
    if (!partyRows.isEmpty()) {
      var party = partyRows.getFirst();
      String gstin = (String) party.get("gstin");
      bill.put("party_gstin", gstin);
      bill.put("party_address", party.get("address"));
      bill.put("party_mobile", party.get("mobile"));
      if (gstin != null && gstin.length() >= 2) {
        bill.put("party_state_code", gstin.substring(0, 2));
      }
    }
    return bill;
  }

  /**
   * Returns complete Delivery Challan detail strictly scoped to the logged-in customer's order
   * trips or the logged-in driver's assigned trips.
   */
  @GetMapping("/trips/{id}")
  Map<String, Object> tripChallan(@PathVariable UUID id) {
    var owner = resolveOwnership();
    if (!owner.role().equals("CUSTOMER") && !owner.role().equals("DRIVER")) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Delivery challans are only available to customers and drivers");
    }
    String ownershipClause =
        owner.role().equals("CUSTOMER") ? "o.customer_id=?" : "t.driver_id=?";
    var list =
        rows(
            "select t.id,t.status,t.source,t.planned_date,t.planned_quantity,t.loaded_quantity,"
                + " t.received_quantity,t.accepted_quantity,t.loaded_gross,t.loaded_tare,"
                + " t.received_gross,t.received_tare,t.loading_slip,t.delivery_slip,"
                + " t.receiver_name,t.proof_reference,t.truck_registration,t.driver_name,"
                + " t.distance_km,t.eway_bill_number,t.eway_bill_valid_until,"
                + " i.material,o.delivery_site,o.branch_id,c.name as customer_name,"
                + " c.gstin as customer_gstin"
                + " from trips t join sales_order_items i on i.id=t.order_item_id"
                + " join sales_orders o on o.id=i.order_id"
                + " join customers c on c.id=o.customer_id"
                + " where t.id=? and "
                + ownershipClause,
            id,
            owner.recordId());
    if (list.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip challan not found");
    }
    var trip = new LinkedHashMap<>(list.getFirst());
    UUID branchId = (UUID) trip.get("branch_id");
    if (branchId != null) {
      var branchRows =
          rows(
              "select id,code,name,address,city,state,pincode,gstin,state_code from branches"
                  + " where id=?",
              branchId);
      if (!branchRows.isEmpty()) trip.put("branch", branchRows.getFirst());
    }
    return trip;
  }

  // --- Driver Mobile Execution Endpoints (strictly scoped to assigned driver) ---

  @PostMapping("/driver/trips/{id}/start-loading")
  @PreAuthorize("hasRole('DRIVER')")
  Map<String, Object> driverStartLoading(
      @PathVariable UUID id, @Valid @RequestBody DriverEvent r) {
    requireDriverOwnsTrip(id);
    return driverTrips.startLoading(id, r.eventId());
  }

  @PostMapping("/driver/trips/{id}/load")
  @PreAuthorize("hasRole('DRIVER')")
  Map<String, Object> driverLoad(@PathVariable UUID id, @Valid @RequestBody DriverLoad r) {
    requireDriverOwnsTrip(id);
    return driverTrips.load(id, r.eventId(), r.gross(), r.tare(), r.slipReference());
  }

  @PostMapping("/driver/trips/{id}/dispatch")
  @PreAuthorize("hasRole('DRIVER')")
  Map<String, Object> driverDispatch(@PathVariable UUID id, @Valid @RequestBody DriverEvent r) {
    requireDriverOwnsTrip(id);
    return driverTrips.dispatch(id, r.eventId());
  }

  @PostMapping("/driver/trips/{id}/arrive")
  @PreAuthorize("hasRole('DRIVER')")
  Map<String, Object> driverArrive(@PathVariable UUID id, @Valid @RequestBody DriverEvent r) {
    requireDriverOwnsTrip(id);
    return driverTrips.arrive(id, r.eventId());
  }

  @PostMapping("/driver/trips/{id}/start-unloading")
  @PreAuthorize("hasRole('DRIVER')")
  Map<String, Object> driverStartUnloading(
      @PathVariable UUID id, @Valid @RequestBody DriverEvent r) {
    requireDriverOwnsTrip(id);
    return driverTrips.startUnloading(id, r.eventId());
  }

  @PostMapping("/driver/trips/{id}/deliver")
  @PreAuthorize("hasRole('DRIVER')")
  Map<String, Object> driverDeliver(@PathVariable UUID id, @Valid @RequestBody DriverDeliver r) {
    requireDriverOwnsTrip(id);
    return driverTrips.deliver(
        id,
        r.eventId(),
        r.gross(),
        r.tare(),
        r.acceptedQuantity(),
        r.slipReference(),
        r.receiverName(),
        r.proofReference(),
        r.discrepancyReason());
  }

  @PostMapping(
      value = "/driver/trips/{id}/documents",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('DRIVER')")
  ResponseEntity<Map<String, Object>> driverUploadDocument(
      @PathVariable UUID id,
      @RequestParam UUID documentId,
      @RequestPart MultipartFile file) {
    requireDriverOwnsTrip(id);
    var result = tripDocuments.uploadTripDocument(documentId, id, file);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .cacheControl(CacheControl.noStore())
        .body(result);
  }

  @GetMapping("/driver/trips/{id}/documents")
  @PreAuthorize("hasRole('DRIVER')")
  ResponseEntity<List<Map<String, Object>>> driverListDocuments(@PathVariable UUID id) {
    requireDriverOwnsTrip(id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(tripDocuments.listTripDocuments(id));
  }

  private List<Map<String, Object>> bills(UUID party, String kind) {
    return rows(
        "select p.id,p.kind,p.prepared_on,p.subtotal,p.status,t.invoice_number,t.payable_total,"
            + " coalesce((select sum(a.amount) from invoice_allocations a where"
            + " a.preparation_id=p.id and a.status='ACTIVE'),0) as allocated_amount,"
            + " case when t.payable_total is null then null else t.payable_total -"
            + " coalesce((select sum(a.amount) from invoice_allocations a where"
            + " a.preparation_id=p.id and a.status='ACTIVE'),0) end as outstanding_amount from"
            + " billing_preparations p left join billing_tax_assessments t on"
            + " t.preparation_id=p.id where p.party_id=? and p.kind=? and p.status='READY' order by"
            + " p.prepared_on desc,p.id"
            + " limit 50",
        party,
        kind);
  }
}
