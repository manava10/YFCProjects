package com.yfctransport.fuel;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.fleet.TruckRegistry;
import com.yfctransport.logistics.TripExpenseContext;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
class OperatingService {
  private final JdbcTemplate db;
  private final TruckRegistry trucks;
  private final TripExpenseContext trips;
  private final AuditTrail audit;
  private final Clock clock;
  private final ObjectMapper json;

  OperatingService(
      JdbcTemplate db,
      TruckRegistry trucks,
      TripExpenseContext trips,
      AuditTrail audit,
      Clock clock,
      ObjectMapper json) {
    this.db = db;
    this.trucks = trucks;
    this.trips = trips;
    this.audit = audit;
    this.clock = clock;
    this.json = json;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private String clean(String v) {
    return v == null || v.isBlank() ? null : v.strip();
  }

  private String n(BigDecimal v) {
    return v == null ? null : v.stripTrailingZeros().toPlainString();
  }

  private String payload(Object... parts) {
    return json.writeValueAsString(Arrays.asList(parts));
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private String table(String kind) {
    return kind.equals("FUEL") ? "fuel_entries" : "operating_expenses";
  }

  private Map<String, Object> normalize(Map<String, Object> row) {
    Map<String, Object> r = new LinkedHashMap<>();
    row.forEach((k, v) -> r.put(k.toLowerCase(Locale.ROOT), v));
    return r;
  }

  private Map<String, Object> one(String table, UUID id, boolean lock) {
    var rows =
        db.queryForList("select * from " + table + " where id=?" + (lock ? " for update" : ""), id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operating record not found");
    return normalize(rows.getFirst());
  }

  private UUID uuid(Map<String, Object> r, String k) {
    return (UUID) r.get(k);
  }

  private BigDecimal decimal(Map<String, Object> r, String k) {
    return (BigDecimal) r.get(k);
  }

  private Instant instant(Map<String, Object> r, String key) {
    Object value = r.get(key);
    return value == null
        ? null
        : value instanceof OffsetDateTime d
            ? d.toInstant()
            : ((java.sql.Timestamp) value).toInstant();
  }

  private String camel(String key) {
    String[] p = key.split("_");
    StringBuilder b = new StringBuilder(p[0]);
    for (int i = 1; i < p.length; i++)
      b.append(Character.toUpperCase(p[i].charAt(0))).append(p[i].substring(1));
    return b.toString();
  }

  private Map<String, Object> view(String kind, UUID id, boolean replay) {
    var r = one(table(kind), id, false);
    r.remove("create_payload");
    Map<String, Object> out = new LinkedHashMap<>();
    r.forEach((k, v) -> out.put(camel(k), v));
    out.put("currency", "INR");
    out.put("replayed", replay);
    out.put(
        "reversed",
        db.queryForObject(
                "select count(*) from operating_reversals where kind=? and source_id=? and"
                    + " status='APPROVED'",
                Integer.class,
                kind,
                id)
            > 0);
    return out;
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(String kind, UUID id) {
    return view(kind, id, false);
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(String kind, int page, int size) {
    return db
        .query(
            "select id from " + table(kind) + " order by id limit ? offset ?",
            (r, n) -> r.getObject(1, UUID.class),
            size,
            (long) page * size)
        .stream()
        .map(id -> view(kind, id, false))
        .toList();
  }

  private TripExpenseContext.Context context(UUID trip, UUID truck) {
    if (trip == null) return null;
    var c = trips.lock(trip);
    if (!c.truckId().equals(truck))
      throw new IllegalArgumentException("Truck does not belong to trip");
    return c;
  }

  private void occurred(Instant at, TripExpenseContext.Context trip) {
    if (at == null)
      throw new IllegalArgumentException("An actual occurrence timestamp is required");
    if (at.isAfter(clock.instant()))
      throw new IllegalArgumentException("Occurrence cannot be in the future");
    if (trip != null) {
      if (trip.status().equals("CANCELLED"))
        throw conflict("Cancelled trips cannot receive approved operating costs");
      if (at.isBefore(trip.createdAt())
          || trip.deliveredAt() != null && at.isAfter(trip.deliveredAt()))
        throw new IllegalArgumentException(
            "Occurrence must fall within the trip's allocation-to-delivery window");
    }
  }

  private Map<String, Object> rate(UUID id, LocalDate date) {
    var r = one("diesel_rates", id, true);
    if (((java.sql.Date) r.get("effective_date")).toLocalDate().isAfter(date))
      throw new IllegalArgumentException("Rate is not effective on filling date");
    return r;
  }

  private BigDecimal amount(BigDecimal quantity, BigDecimal price) {
    var amount = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
    if (amount.signum() <= 0)
      throw new IllegalArgumentException("Fuel receipt amount must round to at least INR 0.01");
    return amount;
  }

  private void fuelValid(
      LocalDate day,
      Instant at,
      BigDecimal qty,
      BigDecimal capacity,
      BigDecimal price,
      BigDecimal reference,
      String variance,
      TripExpenseContext.Context trip) {
    if (day.isAfter(LocalDate.now(clock)))
      throw new IllegalArgumentException("Filling date cannot be in the future");
    if (qty.compareTo(capacity) > 0)
      throw conflict("A single fill cannot exceed truck tank capacity");
    if (price.compareTo(reference) != 0 && clean(variance) == null)
      throw new IllegalArgumentException(
          "Actual price differs from reference rate; record a reason");
    if (at != null) {
      occurred(at, trip);
      if (!at.atZone(clock.getZone()).toLocalDate().equals(day))
        throw new IllegalArgumentException("Filling timestamp and date must agree in Asia/Kolkata");
    } else if (trip != null && trip.status().equals("CANCELLED"))
      throw conflict("Cancelled trip cannot receive fuel submissions");
  }

  Map<String, Object> createFuel(OperatingDtos.FuelInput r) {
    var c = context(r.tripId(), r.truckId());
    var capacity = trucks.lockTankCapacity(r.truckId());
    UUID id = r.entryId() == null ? UUID.randomUUID() : r.entryId();
    String body =
        payload(
            r.truckId(),
            r.rateId(),
            r.tripId(),
            r.receiptReference().strip(),
            r.filledOn(),
            n(r.quantityLitres()),
            n(r.actualPricePerLitre()),
            n(r.odometer()),
            r.filledAt(),
            clean(r.receiptEvidence()),
            clean(r.priceVarianceReason()));
    if (!db.queryForList("select id from fuel_entries where id=?", id).isEmpty()) {
      if (!body.equals(one("fuel_entries", id, false).get("create_payload")))
        throw conflict("Fuel ID was already used with different details");
      return view("FUEL", id, true);
    }
    var rate = rate(r.rateId(), r.filledOn());
    var reference = decimal(rate, "price_per_litre");
    fuelValid(
        r.filledOn(),
        r.filledAt(),
        r.quantityLitres(),
        capacity,
        r.actualPricePerLitre(),
        reference,
        r.priceVarianceReason(),
        c);
    db.update(
        "insert into"
            + " fuel_entries(id,truck_id,rate_id,receipt_reference,filled_on,quantity_litres,actual_price_per_litre,amount,trip_id,odometer,filled_at,receipt_evidence,price_variance_reason,reference_price_per_litre,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        r.truckId(),
        r.rateId(),
        r.receiptReference().strip(),
        r.filledOn(),
        r.quantityLitres(),
        r.actualPricePerLitre(),
        amount(r.quantityLitres(), r.actualPricePerLitre()),
        r.tripId(),
        r.odometer(),
        r.filledAt() == null ? null : java.sql.Timestamp.from(r.filledAt()),
        clean(r.receiptEvidence()),
        clean(r.priceVarianceReason()),
        reference,
        actor(),
        body);
    audit.record(
        "FUEL_ENTRY_CREATED", "FUEL_ENTRY", id, "Fuel submission pending independent verification");
    return view("FUEL", id, false);
  }

  private void category(OperatingDtos.Category category, UUID trip) {
    if (Set.of(
                OperatingDtos.Category.TOLL,
                OperatingDtos.Category.DRIVER_ALLOWANCE,
                OperatingDtos.Category.PARKING)
            .contains(category)
        && trip == null)
      throw new IllegalArgumentException("This expense category requires a trip");
  }

  Map<String, Object> createExpense(OperatingDtos.ExpenseInput r) {
    var c = context(r.tripId(), r.truckId());
    trucks.lockTankCapacity(r.truckId());
    String body =
        payload(
            r.truckId(),
            r.tripId(),
            r.category(),
            r.occurredAt(),
            n(r.amount()),
            r.description().strip(),
            r.receiptReference().strip(),
            clean(r.receiptEvidence()));
    if (!db.queryForList("select id from operating_expenses where id=?", r.expenseId()).isEmpty()) {
      if (!body.equals(one("operating_expenses", r.expenseId(), false).get("create_payload")))
        throw conflict("Expense ID was already used with different details");
      return view("EXPENSE", r.expenseId(), true);
    }
    category(r.category(), r.tripId());
    occurred(r.occurredAt(), c);
    db.update(
        "insert into"
            + " operating_expenses(id,truck_id,trip_id,category,occurred_at,amount,description,receipt_reference,receipt_evidence,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?)",
        r.expenseId(),
        r.truckId(),
        r.tripId(),
        r.category().name(),
        java.sql.Timestamp.from(r.occurredAt()),
        r.amount(),
        r.description().strip(),
        r.receiptReference().strip(),
        clean(r.receiptEvidence()),
        actor(),
        body);
    audit.record(
        "EXPENSE_SUBMITTED",
        "EXPENSE",
        r.expenseId(),
        "Operating expense pending independent verification");
    return view("EXPENSE", r.expenseId(), false);
  }

  private record Locked(
      Map<String, Object> row, TripExpenseContext.Context context, BigDecimal capacity) {}

  private Locked lock(String kind, UUID id) {
    // Scalar identity lookup only; mutable state is read after the trip/truck locks.
    var links = db.queryForList("select truck_id,trip_id from " + table(kind) + " where id=?", id);
    if (links.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operating record not found");
    var link = normalize(links.getFirst());
    var c = context(uuid(link, "trip_id"), uuid(link, "truck_id"));
    var capacity = trucks.lockTankCapacity(uuid(link, "truck_id"));
    return new Locked(one(table(kind), id, true), c, capacity);
  }

  private boolean replay(
      UUID event, String kind, UUID id, String action, int revision, String body) {
    var rows = db.queryForList("select * from operating_events where id=?", event);
    if (rows.isEmpty()) return false;
    var r = normalize(rows.getFirst());
    if (!kind.equals(r.get("kind"))
        || !id.equals(r.get("record_id"))
        || !action.equals(r.get("action"))
        || ((Number) r.get("revision")).intValue() != revision
        || !body.equals(r.get("payload")))
      throw conflict("Event ID was already used with different details");
    return true;
  }

  private void event(
      UUID event, String kind, UUID id, String action, int revision, String body, String snapshot) {
    db.update(
        "insert into"
            + " operating_events(id,kind,record_id,action,revision,payload,previous_snapshot,actor)"
            + " values(?,?,?,?,?,?,?,?)",
        event,
        kind,
        id,
        action,
        revision,
        body,
        snapshot,
        actor());
    audit.record(kind + "_" + action, kind, id, "Revision=" + revision + "; event=" + event);
  }

  private void odometer(Map<String, Object> r) {
    BigDecimal odo = decimal(r, "odometer");
    Instant at = instant(r, "filled_at");
    UUID truck = uuid(r, "truck_id"), id = uuid(r, "id");
    var applied =
        db.queryForList(
            "select new_reading,reset_at from odometer_resets where truck_id=? and"
                + " status='APPROVED' and reset_at<=? order by reset_at desc,submitted_at desc,id"
                + " desc limit 1",
            truck,
            java.sql.Timestamp.from(at));
    BigDecimal floor = null;
    java.sql.Timestamp afterBound = java.sql.Timestamp.from(Instant.EPOCH);
    if (!applied.isEmpty()) {
      var reset = normalize(applied.getFirst());
      floor = decimal(reset, "new_reading");
      afterBound = java.sql.Timestamp.from(instant(reset, "reset_at"));
    }
    java.sql.Timestamp beforeBound = java.sql.Timestamp.from(Instant.parse("9999-12-31T23:59:59Z"));
    var next =
        db.queryForList(
            "select reset_at from odometer_resets where truck_id=? and status='APPROVED' and"
                + " reset_at>? order by reset_at,id limit 1",
            truck,
            java.sql.Timestamp.from(at));
    if (!next.isEmpty())
      beforeBound = java.sql.Timestamp.from(instant(normalize(next.getFirst()), "reset_at"));
    BigDecimal before =
        db.queryForObject(
            "select max(odometer) from fuel_entries where truck_id=? and id<>? and"
                + " status='APPROVED' and filled_at<? and filled_at>? and filled_at<?",
            BigDecimal.class,
            truck,
            id,
            java.sql.Timestamp.from(at),
            afterBound,
            beforeBound);
    BigDecimal after =
        db.queryForObject(
            "select min(odometer) from fuel_entries where truck_id=? and id<>? and"
                + " status='APPROVED' and filled_at>? and filled_at>? and filled_at<?",
            BigDecimal.class,
            truck,
            id,
            java.sql.Timestamp.from(at),
            afterBound,
            beforeBound);
    Integer different =
        db.queryForObject(
            "select count(*) from fuel_entries where truck_id=? and id<>? and status='APPROVED' and"
                + " filled_at=? and odometer<>? and filled_at>? and filled_at<?",
            Integer.class,
            truck,
            id,
            java.sql.Timestamp.from(at),
            odo,
            afterBound,
            beforeBound);
    if (floor != null && odo.compareTo(floor) < 0
        || before != null && odo.compareTo(before) < 0
        || after != null && odo.compareTo(after) > 0
        || different > 0) throw conflict("Odometer conflicts with approved filling chronology");
  }

  Map<String, Object> decide(String kind, UUID id, boolean approve, OperatingDtos.Decision r) {
    var locked = lock(kind, id);
    var row = locked.row();
    String action = approve ? "APPROVE" : "REJECT", body = payload(r.reason().strip());
    if (replay(r.eventId(), kind, id, action, r.revision(), body)) return view(kind, id, true);
    if (!row.get("status").equals("PENDING_VERIFICATION")
        || ((Number) row.get("revision")).intValue() != r.revision())
      throw conflict("Only the current pending revision may be reviewed");
    if (actor().equals(row.get("submitted_by")))
      throw conflict("A different user must review this submission");
    if (approve) {
      if (clean((String) row.get("receipt_evidence")) == null)
        throw new IllegalArgumentException(
            "Receipt or internal voucher evidence reference is required");
      if (kind.equals("FUEL")) {
        if (row.get("odometer") == null || row.get("filled_at") == null)
          throw new IllegalArgumentException(
              "Filling timestamp and odometer are required before approval");
        fuelValid(
            ((java.sql.Date) row.get("filled_on")).toLocalDate(),
            instant(row, "filled_at"),
            decimal(row, "quantity_litres"),
            locked.capacity(),
            decimal(row, "actual_price_per_litre"),
            decimal(row, "reference_price_per_litre"),
            (String) row.get("price_variance_reason"),
            locked.context());
        odometer(row);
      } else occurred(instant(row, "occurred_at"), locked.context());
    }
    db.update(
        "update "
            + table(kind)
            + " set status=?,reviewed_by=?,reviewed_at=current_timestamp,decision_reason=? where"
            + " id=?",
        approve ? "APPROVED" : "REJECTED",
        actor(),
        r.reason().strip(),
        id);
    event(r.eventId(), kind, id, action, r.revision(), body, json.writeValueAsString(row));
    return view(kind, id, false);
  }

  private void revisable(Map<String, Object> row, int revision) {
    if (row.get("status").equals("APPROVED")
        || ((Number) row.get("revision")).intValue() != revision)
      throw conflict("Only the current pending or rejected revision can be revised");
  }

  Map<String, Object> reviseFuel(UUID id, OperatingDtos.FuelRevision r) {
    var locked = lock("FUEL", id);
    var row = locked.row();
    String body =
        payload(
            r.reason().strip(),
            r.rateId(),
            r.filledOn(),
            n(r.quantityLitres()),
            n(r.actualPricePerLitre()),
            n(r.odometer()),
            r.filledAt(),
            r.receiptEvidence().strip(),
            clean(r.priceVarianceReason()));
    if (replay(r.eventId(), "FUEL", id, "REVISE", r.revision(), body))
      return view("FUEL", id, true);
    revisable(row, r.revision());
    var price = decimal(rate(r.rateId(), r.filledOn()), "price_per_litre");
    fuelValid(
        r.filledOn(),
        r.filledAt(),
        r.quantityLitres(),
        locked.capacity(),
        r.actualPricePerLitre(),
        price,
        r.priceVarianceReason(),
        locked.context());
    db.update(
        "update fuel_entries set"
            + " rate_id=?,filled_on=?,quantity_litres=?,actual_price_per_litre=?,amount=?,odometer=?,filled_at=?,receipt_evidence=?,price_variance_reason=?,reference_price_per_litre=?,submitted_by=?,revision=revision+1,status='PENDING_VERIFICATION',reviewed_by=null,reviewed_at=null,decision_reason=null"
            + " where id=?",
        r.rateId(),
        r.filledOn(),
        r.quantityLitres(),
        r.actualPricePerLitre(),
        amount(r.quantityLitres(), r.actualPricePerLitre()),
        r.odometer(),
        java.sql.Timestamp.from(r.filledAt()),
        r.receiptEvidence().strip(),
        clean(r.priceVarianceReason()),
        price,
        actor(),
        id);
    event(r.eventId(), "FUEL", id, "REVISE", r.revision(), body, json.writeValueAsString(row));
    return view("FUEL", id, false);
  }

  Map<String, Object> reviseExpense(UUID id, OperatingDtos.ExpenseRevision r) {
    var locked = lock("EXPENSE", id);
    var row = locked.row();
    String body =
        payload(
            r.reason().strip(),
            r.category(),
            r.occurredAt(),
            n(r.amount()),
            r.description().strip(),
            r.receiptEvidence().strip());
    if (replay(r.eventId(), "EXPENSE", id, "REVISE", r.revision(), body))
      return view("EXPENSE", id, true);
    revisable(row, r.revision());
    category(r.category(), uuid(row, "trip_id"));
    occurred(r.occurredAt(), locked.context());
    db.update(
        "update operating_expenses set"
            + " category=?,occurred_at=?,amount=?,description=?,receipt_evidence=?,submitted_by=?,revision=revision+1,status='PENDING_VERIFICATION',reviewed_by=null,reviewed_at=null,decision_reason=null"
            + " where id=?",
        r.category().name(),
        java.sql.Timestamp.from(r.occurredAt()),
        r.amount(),
        r.description().strip(),
        r.receiptEvidence().strip(),
        actor(),
        id);
    event(r.eventId(), "EXPENSE", id, "REVISE", r.revision(), body, json.writeValueAsString(row));
    return view("EXPENSE", id, false);
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> history(String kind, UUID id) {
    one(table(kind), id, false);
    return db
        .queryForList(
            "select * from operating_events where kind=? and record_id=? order by created_at,id",
            kind,
            id)
        .stream()
        .map(this::normalize)
        .toList();
  }

  Map<String, Object> correctRate(UUID id, OperatingDtos.CorrectRate r) {
    var rate = one("diesel_rates", id, true);
    var existing = db.queryForList("select * from diesel_rate_corrections where id=?", r.eventId());
    if (!existing.isEmpty()) {
      var old = normalize(existing.getFirst());
      if (!id.equals(uuid(old, "rate_id"))
          || decimal(old, "old_price").compareTo(r.expectedPrice()) != 0
          || decimal(old, "new_price").compareTo(r.newPrice()) != 0
          || !r.reason().strip().equals(old.get("reason")))
        throw conflict("Correction ID was already used with different details");
      old.put("replayed", true);
      return old;
    }
    if (decimal(rate, "price_per_litre").compareTo(r.expectedPrice()) != 0)
      throw conflict("Rate changed; reload before correcting it");
    if (r.newPrice().compareTo(r.expectedPrice()) == 0)
      throw new IllegalArgumentException("Correction must change the price");
    db.update(
        "insert into diesel_rate_corrections(id,rate_id,old_price,new_price,reason,actor)"
            + " values(?,?,?,?,?,?)",
        r.eventId(),
        id,
        r.expectedPrice(),
        r.newPrice(),
        r.reason().strip(),
        actor());
    db.update("update diesel_rates set price_per_litre=? where id=?", r.newPrice(), id);
    audit.record("DIESEL_RATE_CORRECTED", "DIESEL_RATE", id, "Correction=" + r.eventId());
    var result = one("diesel_rate_corrections", r.eventId(), false);
    result.put("replayed", false);
    return result;
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> rateHistory(UUID id) {
    one("diesel_rates", id, false);
    return db
        .queryForList(
            "select * from diesel_rate_corrections where rate_id=? order by created_at,id", id)
        .stream()
        .map(this::normalize)
        .toList();
  }

  Map<String, Object> costs(UUID trip) {
    var c = trips.lock(trip);
    BigDecimal fuel =
        db.queryForObject(
            "select coalesce((select sum(amount) from fuel_entries where trip_id=? and"
                + " status='APPROVED' and id not in (select source_id from operating_reversals where"
                + " kind='FUEL' and status='APPROVED')),0)+coalesce((select sum(a.amount) from"
                + " fuel_allocations a join fuel_entries f on f.id=a.fuel_id where a.trip_id=? and"
                + " a.status='APPROVED' and f.status='APPROVED' and f.id not in (select source_id"
                + " from operating_reversals where kind='FUEL' and status='APPROVED')),0)",
            BigDecimal.class,
            trip,
            trip);
    BigDecimal other =
        db.queryForObject(
            "select coalesce(sum(amount),0) from operating_expenses where trip_id=? and"
                + " status='APPROVED' and id not in (select source_id from operating_reversals where"
                + " kind='EXPENSE' and status='APPROVED')",
            BigDecimal.class,
            trip);
    BigDecimal litres =
        db.queryForObject(
            "select coalesce((select sum(quantity_litres) from fuel_entries where trip_id=? and"
                + " status='APPROVED' and id not in (select source_id from operating_reversals where"
                + " kind='FUEL' and status='APPROVED')),0)+coalesce((select sum(a.quantity_litres)"
                + " from fuel_allocations a join fuel_entries f on f.id=a.fuel_id where"
                + " a.trip_id=? and a.status='APPROVED' and f.status='APPROVED' and f.id not in"
                + " (select source_id from operating_reversals where kind='FUEL' and"
                + " status='APPROVED')),0)",
            BigDecimal.class,
            trip,
            trip);
    Integer pending =
        db.queryForObject(
            "select (select count(*) from fuel_entries where trip_id=? and"
                + " status='PENDING_VERIFICATION')+(select count(*) from operating_expenses where"
                + " trip_id=? and status='PENDING_VERIFICATION')+(select count(*) from"
                + " fuel_allocations where trip_id=? and status='PENDING')",
            Integer.class,
            trip,
            trip,
            trip);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tripId", trip);
    result.put("currency", "INR");
    result.put("approvedFuelCost", fuel);
    result.put("approvedOtherOperatingCost", other);
    result.put("totalApprovedOperatingCost", fuel.add(other));
    result.put("approvedFuelPurchasedLitres", litres);
    result.put("estimatedFuelLitres", c.estimatedFuelLitres());
    result.put("pendingRecordCount", pending);
    result.put("accountingPosted", false);
    return result;
  }

  Map<String, Object> reverse(String kind, UUID source, OperatingDtos.Reversal r) {
    lock(kind, source);
    String payload =
        json.writeValueAsString(
            List.of(kind, source, r.reason().strip(), r.evidenceReference().strip()));
    if (db.queryForObject(
            "select count(*) from operating_reversals where id=?", Integer.class, r.reversalId())
        > 0) {
      var old = one("operating_reversals", r.reversalId(), false);
      if (!payload.equals(old.get("create_payload")))
        throw conflict("Reversal ID used with different data");
      old.put("replayed", true);
      old.remove("create_payload");
      return old;
    }
    var row = one(table(kind), source, true);
    if (!"APPROVED".equals(row.get("status")))
      throw conflict("Only an approved operating record can be reversed");
    if ("FUEL".equals(kind)
        && db.queryForObject(
                "select count(*) from fuel_allocations where fuel_id=? and status='PENDING'",
                Integer.class,
                source)
            > 0)
      throw conflict("Pending fuel allocations must be rejected before reversing this receipt");
    if (db.queryForObject(
            "select count(*) from operating_reversal_claims where kind=? and source_id=?",
            Integer.class,
            kind,
            source)
        > 0) throw conflict("A reversal is already pending or approved for this record");
    db.update(
        "insert into"
            + " operating_reversals(id,kind,source_id,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?)",
        r.reversalId(),
        kind,
        source,
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        payload);
    db.update(
        "insert into operating_reversal_claims(kind,source_id,reversal_id) values(?,?,?)",
        kind,
        source,
        r.reversalId());
    audit.record("OPERATING_REVERSAL_SUBMITTED", kind, source, "Reversal=" + r.reversalId());
    var result = one("operating_reversals", r.reversalId(), false);
    result.remove("create_payload");
    result.put("replayed", false);
    return result;
  }

  Map<String, Object> decideReverse(UUID id, OperatingDtos.Decision r, boolean approve) {
    var record = one("operating_reversals", id, true);
    String action = approve ? "APPROVE" : "REJECT";
    var old = db.queryForList("select * from operating_reversal_events where id=?", r.eventId());
    if (!old.isEmpty()) {
      var event = new LinkedHashMap<String, Object>();
      old.getFirst().forEach((k, v) -> event.put(k.toLowerCase(Locale.ROOT), v));
      if (!id.equals(event.get("reversal_id"))
          || !action.equals(event.get("action"))
          || !r.reason().equals(event.get("reason"))
          || !actor().equals(event.get("actor")))
        throw conflict("Review event ID used with different data or actor");
      record.remove("create_payload");
      record.put("replayed", true);
      return record;
    }
    if (!"PENDING".equals(record.get("status"))) throw conflict("Reversal already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the reversal");
    db.update(
        "insert into operating_reversal_events(id,reversal_id,action,reason,actor) values(?,?,?,?,?)",
        r.eventId(),
        id,
        action,
        r.reason(),
        actor());
    db.update(
        "update operating_reversals set status=?,reviewed_by=?,reviewed_at=current_timestamp,review_reason=? where id=?",
        approve ? "APPROVED" : "REJECTED",
        actor(),
        r.reason(),
        id);
    if (!approve) db.update("delete from operating_reversal_claims where reversal_id=?", id);
    audit.record(
        approve ? "OPERATING_REVERSAL_APPROVED" : "OPERATING_REVERSAL_REJECTED",
        (String) record.get("kind"),
        (UUID) record.get("source_id"),
        "Event=" + r.eventId());
    var result = one("operating_reversals", id, false);
    result.remove("create_payload");
    result.put("replayed", false);
    return result;
  }

  private Map<String, Object> claimView(
      String table, UUID id, String eventKey, String events, boolean replay) {
    var row = one(table, id, false);
    row.remove("create_payload");
    Map<String, Object> out = new LinkedHashMap<>();
    row.forEach((k, v) -> out.put(camel(k), v));
    out.put("replayed", replay);
    out.put(
        "events",
        db.queryForList(
            "select id,action,reason,actor,occurred_at from "
                + events
                + " where "
                + eventKey
                + "=? order by occurred_at,id",
            id));
    return out;
  }

  private List<Map<String, Object>> claimList(
      String table, String eventKey, String events, int page, int size) {
    return db
        .query(
            "select id from " + table + " order by submitted_at,id limit ? offset ?",
            (r, n) -> r.getObject(1, UUID.class),
            size,
            (long) page * size)
        .stream()
        .map(id -> claimView(table, id, eventKey, events, false))
        .toList();
  }

  private boolean replayReview(
      String events, UUID event, UUID record, String key, String action, String reason) {
    var rows = db.queryForList("select * from " + events + " where id=?", event);
    if (rows.isEmpty()) return false;
    var e = normalize(rows.getFirst());
    if (!record.equals(e.get(key))
        || !action.equals(e.get("action"))
        || !reason.equals(e.get("reason"))
        || !actor().equals(e.get("actor")))
      throw conflict("Review event ID used with different data or actor");
    return true;
  }

  private void requireResetFitsApprovedReadings(
      UUID truck, Instant resetAt, BigDecimal newReading) {
    java.sql.Timestamp afterBound = java.sql.Timestamp.from(resetAt);
    var next =
        db.queryForList(
            "select reset_at from odometer_resets where truck_id=? and status='APPROVED' and"
                + " reset_at>? order by reset_at,id limit 1",
            truck,
            afterBound);
    java.sql.Timestamp beforeBound = java.sql.Timestamp.from(Instant.parse("9999-12-31T23:59:59Z"));
    if (!next.isEmpty())
      beforeBound = java.sql.Timestamp.from(instant(normalize(next.getFirst()), "reset_at"));
    BigDecimal later =
        db.queryForObject(
            "select min(odometer) from fuel_entries where truck_id=? and status='APPROVED' and"
                + " filled_at>=? and filled_at<?",
            BigDecimal.class,
            truck,
            afterBound,
            beforeBound);
    if (later != null && later.compareTo(newReading) < 0)
      throw conflict("Odometer reset conflicts with approved filling chronology");
  }

  Map<String, Object> resetOdometer(OperatingDtos.OdometerReset r) {
    trucks.lockTankCapacity(r.truckId());
    String body =
        payload(
            r.truckId(),
            n(r.newReading()),
            r.resetAt(),
            r.reason().strip(),
            r.evidenceReference().strip());
    if (db.queryForObject(
            "select count(*) from odometer_resets where id=?", Integer.class, r.resetId())
        > 0) {
      if (!body.equals(one("odometer_resets", r.resetId(), false).get("create_payload")))
        throw conflict("Record ID used with different data");
      return claimView(
          "odometer_resets", r.resetId(), "reset_id", "odometer_reset_events", true);
    }
    if (r.resetAt().isAfter(clock.instant()))
      throw new IllegalArgumentException("Odometer reset cannot be in the future");
    requireResetFitsApprovedReadings(r.truckId(), r.resetAt(), r.newReading());
    if (db.queryForObject(
            "select count(*) from odometer_reset_claims where truck_id=?",
            Integer.class,
            r.truckId())
        > 0) throw conflict("A pending odometer reset already exists for this truck");
    db.update(
        "insert into"
            + " odometer_resets(id,truck_id,new_reading,reset_at,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?)",
        r.resetId(),
        r.truckId(),
        r.newReading(),
        java.sql.Timestamp.from(r.resetAt()),
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        body);
    db.update(
        "insert into odometer_reset_claims(truck_id,reset_id) values(?,?)",
        r.truckId(),
        r.resetId());
    audit.record("ODOMETER_RESET_SUBMITTED", "ODOMETER_RESET", r.resetId(), "Truck=" + r.truckId());
    return claimView("odometer_resets", r.resetId(), "reset_id", "odometer_reset_events", false);
  }

  Map<String, Object> odometerReset(UUID id) {
    return claimView("odometer_resets", id, "reset_id", "odometer_reset_events", false);
  }

  List<Map<String, Object>> listResets(int page, int size) {
    return claimList("odometer_resets", "reset_id", "odometer_reset_events", page, size);
  }

  Map<String, Object> decideReset(UUID id, OperatingDtos.Review r, boolean approve) {
    trucks.lockTankCapacity(uuid(one("odometer_resets", id, false), "truck_id"));
    var record = one("odometer_resets", id, true);
    String action = approve ? "APPROVE" : "REJECT";
    if (replayReview("odometer_reset_events", r.eventId(), id, "reset_id", action, r.reason()))
      return claimView("odometer_resets", id, "reset_id", "odometer_reset_events", true);
    if (!"PENDING".equals(record.get("status"))) throw conflict("Odometer reset already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the odometer reset");
    if (approve)
      requireResetFitsApprovedReadings(
          uuid(record, "truck_id"), instant(record, "reset_at"), decimal(record, "new_reading"));
    db.update(
        "insert into odometer_reset_events(id,reset_id,action,reason,actor) values(?,?,?,?,?)",
        r.eventId(),
        id,
        action,
        r.reason(),
        actor());
    db.update(
        "update odometer_resets set status=?,reviewed_by=?,reviewed_at=current_timestamp,review_reason=? where id=?",
        approve ? "APPROVED" : "REJECTED",
        actor(),
        r.reason(),
        id);
    db.update("delete from odometer_reset_claims where reset_id=?", id);
    audit.record(
        approve ? "ODOMETER_RESET_APPROVED" : "ODOMETER_RESET_REJECTED",
        "ODOMETER_RESET",
        id,
        "Event=" + r.eventId());
    return claimView("odometer_resets", id, "reset_id", "odometer_reset_events", false);
  }

  Map<String, Object> allocate(UUID fuel, OperatingDtos.FuelAllocation r) {
    var links = db.queryForList("select truck_id,trip_id,status from fuel_entries where id=?", fuel);
    if (links.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operating record not found");
    var link = normalize(links.getFirst());
    var trip = context(r.tripId(), uuid(link, "truck_id"));
    trucks.lockTankCapacity(uuid(link, "truck_id"));
    var entry = one("fuel_entries", fuel, true);
    String body =
        payload(
            fuel,
            r.tripId(),
            n(r.quantityLitres()),
            r.reason().strip(),
            r.evidenceReference().strip());
    if (db.queryForObject(
            "select count(*) from fuel_allocations where id=?", Integer.class, r.allocationId())
        > 0) {
      if (!body.equals(one("fuel_allocations", r.allocationId(), false).get("create_payload")))
        throw conflict("Record ID used with different data");
      return claimView(
          "fuel_allocations", r.allocationId(), "allocation_id", "fuel_allocation_events", true);
    }
    if (!"APPROVED".equals(entry.get("status")))
      throw conflict("Only an approved truck-only fuel receipt can be allocated");
    if (entry.get("trip_id") != null)
      throw conflict("A fuel receipt already assigned to a trip cannot be split");
    if (db.queryForObject(
            "select count(*) from operating_reversals where kind='FUEL' and source_id=? and"
                + " status='APPROVED'",
            Integer.class,
            fuel)
        > 0) throw conflict("A reversed fuel receipt cannot be allocated");
    occurred(instant(entry, "filled_at"), trip);
    BigDecimal used =
        db.queryForObject(
            "select coalesce(sum(quantity_litres),0) from fuel_allocations where fuel_id=? and"
                + " status in ('PENDING','APPROVED')",
            BigDecimal.class,
            fuel);
    if (used.add(r.quantityLitres()).compareTo(decimal(entry, "quantity_litres")) > 0)
      throw conflict("Allocated litres exceed the remaining fuel receipt quantity");
    if (db.queryForObject(
            "select count(*) from fuel_allocation_claims where fuel_id=? and trip_id=?",
            Integer.class,
            fuel,
            r.tripId())
        > 0) throw conflict("This trip already has an allocation from the fuel receipt");
    BigDecimal alreadyAmount =
        db.queryForObject(
            "select coalesce(sum(amount),0) from fuel_allocations where fuel_id=? and status in"
                + " ('PENDING','APPROVED')",
            BigDecimal.class,
            fuel);
    BigDecimal allocated =
        used.add(r.quantityLitres()).compareTo(decimal(entry, "quantity_litres")) == 0
            ? decimal(entry, "amount").subtract(alreadyAmount)
            : amount(r.quantityLitres(), decimal(entry, "actual_price_per_litre"));
    if (allocated.signum() <= 0)
      throw new IllegalArgumentException("Fuel allocation amount must round to at least INR 0.01");
    db.update(
        "insert into"
            + " fuel_allocations(id,fuel_id,trip_id,quantity_litres,amount,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?)",
        r.allocationId(),
        fuel,
        r.tripId(),
        r.quantityLitres(),
        allocated,
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        body);
    db.update(
        "insert into fuel_allocation_claims(fuel_id,trip_id,allocation_id) values(?,?,?)",
        fuel,
        r.tripId(),
        r.allocationId());
    audit.record("FUEL_ALLOCATION_SUBMITTED", "FUEL_ALLOCATION", r.allocationId(), "Fuel=" + fuel);
    return claimView(
        "fuel_allocations", r.allocationId(), "allocation_id", "fuel_allocation_events", false);
  }

  Map<String, Object> allocation(UUID id) {
    return claimView("fuel_allocations", id, "allocation_id", "fuel_allocation_events", false);
  }

  List<Map<String, Object>> listAllocations(int page, int size) {
    return claimList("fuel_allocations", "allocation_id", "fuel_allocation_events", page, size);
  }

  Map<String, Object> decideAllocation(UUID id, OperatingDtos.Review r, boolean approve) {
    var preview = one("fuel_allocations", id, false);
    var links =
        db.queryForList(
            "select truck_id,trip_id from fuel_entries where id=?", uuid(preview, "fuel_id"));
    var link = normalize(links.getFirst());
    context(uuid(preview, "trip_id"), uuid(link, "truck_id"));
    trucks.lockTankCapacity(uuid(link, "truck_id"));
    var record = one("fuel_allocations", id, true);
    var entry = one("fuel_entries", uuid(record, "fuel_id"), true);
    String action = approve ? "APPROVE" : "REJECT";
    if (replayReview(
        "fuel_allocation_events", r.eventId(), id, "allocation_id", action, r.reason()))
      return claimView("fuel_allocations", id, "allocation_id", "fuel_allocation_events", true);
    if (!"PENDING".equals(record.get("status"))) throw conflict("Fuel allocation already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the fuel allocation");
    if (approve) {
      if (!"APPROVED".equals(entry.get("status")) || entry.get("trip_id") != null)
        throw conflict("Only an approved truck-only fuel receipt can be allocated");
      occurred(instant(entry, "filled_at"), context(uuid(record, "trip_id"), uuid(link, "truck_id")));
      BigDecimal used =
          db.queryForObject(
              "select coalesce(sum(quantity_litres),0) from fuel_allocations where fuel_id=? and"
                  + " id<>? and status in ('PENDING','APPROVED')",
              BigDecimal.class,
              uuid(record, "fuel_id"),
              id);
      if (used.add(decimal(record, "quantity_litres")).compareTo(decimal(entry, "quantity_litres"))
          > 0) throw conflict("Allocated litres exceed the remaining fuel receipt quantity");
    }
    db.update(
        "insert into fuel_allocation_events(id,allocation_id,action,reason,actor) values(?,?,?,?,?)",
        r.eventId(),
        id,
        action,
        r.reason(),
        actor());
    db.update(
        "update fuel_allocations set status=?,reviewed_by=?,reviewed_at=current_timestamp,review_reason=? where id=?",
        approve ? "APPROVED" : "REJECTED",
        actor(),
        r.reason(),
        id);
    if (!approve) db.update("delete from fuel_allocation_claims where allocation_id=?", id);
    audit.record(
        approve ? "FUEL_ALLOCATION_APPROVED" : "FUEL_ALLOCATION_REJECTED",
        "FUEL_ALLOCATION",
        id,
        "Event=" + r.eventId());
    return claimView("fuel_allocations", id, "allocation_id", "fuel_allocation_events", false);
  }
}
