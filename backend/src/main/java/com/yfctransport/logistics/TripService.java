package com.yfctransport.logistics;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import com.yfctransport.fleet.FleetAssignments;
import com.yfctransport.inventory.TripSupply;
import com.yfctransport.orders.OrderFulfillment;
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
class TripService {
  private final JdbcTemplate db;
  private final OrderFulfillment orders;
  private final FleetAssignments fleet;
  private final TripSupply supply;
  private final BranchRegistry branches;
  private final AuditTrail audit;
  private final Clock clock;
  private final ObjectMapper json;

  TripService(
      JdbcTemplate db,
      OrderFulfillment orders,
      FleetAssignments fleet,
      TripSupply supply,
      BranchRegistry branches,
      AuditTrail audit,
      Clock clock,
      ObjectMapper json) {
    this.db = db;
    this.orders = orders;
    this.fleet = fleet;
    this.supply = supply;
    this.branches = branches;
    this.audit = audit;
    this.clock = clock;
    this.json = json;
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private Map<String, Object> normalize(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Map<String, Object> trip(UUID id, boolean lock) {
    var rows = db.queryForList("select * from trips where id=?" + (lock ? " for update" : ""), id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    return normalize(rows.getFirst());
  }

  private UUID uuid(Map<String, Object> row, String key) {
    return (UUID) row.get(key);
  }

  private BigDecimal number(Map<String, Object> row, String key) {
    return (BigDecimal) row.get(key);
  }

  private String amount(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private String payload(Object... parts) {
    return json.writeValueAsString(Arrays.asList(parts));
  }

  private Map<String, Object> result(UUID id, boolean replay) {
    var t = trip(id, false);
    t.remove("create_payload");
    t.put("replayed", replay);
    t.put("unit", "TONNE");
    t.put("fuelEstimateOnly", true);
    return t;
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(UUID id) {
    var t = result(id, false);
    t.put(
        "events",
        db
            .queryForList(
                "select id,action,actor,occurred_at from trip_events where trip_id=? order by"
                    + " occurred_at,id",
                id)
            .stream()
            .map(this::normalize)
            .toList());
    return t;
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(int page, int size) {
    return db
        .query(
            "select id from trips order by created_at desc,id desc limit ? offset ?",
            (r, n) -> r.getObject(1, UUID.class),
            size,
            (long) page * size)
        .stream()
        .map(id -> result(id, false))
        .toList();
  }

  Map<String, Object> allocate(TripDtos.Allocate r) {
    var line = orders.lockLine(r.orderItemId());
    String body =
        payload(
            r.orderItemId(),
            r.truckId(),
            r.driverId(),
            r.source(),
            r.reservationId(),
            r.purchaseId(),
            amount(r.quantity()),
            r.plannedDate(),
            amount(r.distanceKm()));
    var prior = db.queryForList("select id,create_payload from trips where id=?", r.tripId());
    if (!prior.isEmpty()) {
      if (!body.equals(prior.getFirst().get("create_payload")))
        throw conflict("Trip ID was already used with different details");
      return result(r.tripId(), true);
    }
    if (r.plannedDate().isBefore(today()))
      throw new IllegalArgumentException("Planned trip date must be today or later");
    branches.requireActive(line.branchId());
    if (r.source() == TripDtos.Source.TRANSPORT) {
      if (!line.businessType().equals("TRANSPORT")
          || r.reservationId() != null
          || r.purchaseId() != null)
        throw new IllegalArgumentException(
            "TRANSPORT requires a transport-only order and no inventory source");
      BigDecimal assigned =
          db.queryForObject(
              "select coalesce(sum(case when loaded_quantity is null then planned_quantity else"
                  + " loaded_quantity end),0) from trips where order_item_id=? and"
                  + " status<>'CANCELLED'",
              BigDecimal.class,
              line.id());
      if (assigned.add(r.quantity()).compareTo(line.quantity()) > 0)
        throw conflict("Trips exceed order item quantity");
    } else {
      if (line.businessType().equals("TRANSPORT"))
        throw new IllegalArgumentException("Transport-only orders cannot consume YFC inventory");
      if (r.source() == TripDtos.Source.YARD
              && (r.reservationId() == null || r.purchaseId() != null)
          || r.source() == TripDtos.Source.DIRECT
              && (r.purchaseId() == null || r.reservationId() != null))
        throw new IllegalArgumentException("Trip source IDs do not match source type");
    }
    orders.claimTripWorkflow(line.id());
    var vehicle = fleet.requireTruck(r.truckId(), line.branchId(), r.plannedDate());
    String driver = fleet.requireDriver(r.driverId(), line.branchId(), r.plannedDate());
    if (r.quantity().compareTo(vehicle.capacity()) > 0)
      throw conflict("Planned quantity exceeds truck capacity");
    BigDecimal estimate = r.distanceKm().divide(vehicle.mileage(), 3, RoundingMode.CEILING);
    db.update(
        "insert into"
            + " trips(id,order_item_id,truck_id,driver_id,source,reservation_id,purchase_id,planned_quantity,planned_date,distance_km,estimated_fuel_litres,truck_registration,driver_name,status,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,'ALLOCATED',?)",
        r.tripId(),
        line.id(),
        r.truckId(),
        r.driverId(),
        r.source().name(),
        r.reservationId(),
        r.purchaseId(),
        r.quantity(),
        r.plannedDate(),
        r.distanceKm(),
        estimate,
        vehicle.registration(),
        driver,
        body);
    fleet.assign(r.tripId(), r.truckId(), r.driverId());
    if (r.source() != TripDtos.Source.TRANSPORT)
      supply.claim(r.tripId(), line.id(), r.reservationId(), r.purchaseId(), r.quantity());
    audit.record(
        "TRIP_ALLOCATED", "TRIP", r.tripId(), "Truck and driver assigned; source=" + r.source());
    return result(r.tripId(), false);
  }

  private Map<String, Object> lock(UUID id) {
    var items =
        db.query(
            "select order_item_id from trips where id=?", (r, n) -> r.getObject(1, UUID.class), id);
    if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    orders.lockLine(items.getFirst());
    return trip(id, true);
  }

  private void requireAcceptedCoversReturns(UUID trip, BigDecimal accepted) {
    BigDecimal returns =
        db.queryForObject(
            "select coalesce(sum(quantity),0) from stock_adjustments where trip_id=? and"
                + " kind='CUSTOMER_RETURN' and status in ('PENDING','APPROVED')",
            BigDecimal.class,
            trip);
    if (accepted.compareTo(returns) < 0)
      throw conflict(
          "Corrected accepted weight cannot be below pending or approved customer returns");
  }

  private boolean replay(UUID id, UUID event, String action, String body) {
    var rows = db.queryForList("select trip_id,action,payload from trip_events where id=?", event);
    if (rows.isEmpty()) return false;
    var old = normalize(rows.getFirst());
    if (!id.equals(old.get("trip_id"))
        || !action.equals(old.get("action"))
        || !body.equals(old.get("payload")))
      throw conflict("Event ID was already used with different details");
    return true;
  }

  private void event(UUID id, UUID event, String action, String body) {
    db.update(
        "insert into trip_events(id,trip_id,action,payload,actor) values(?,?,?,?,?)",
        event,
        id,
        action,
        body,
        actor());
    audit.record("TRIP_" + action, "TRIP", id, "Trip event=" + event);
  }

  private void state(Map<String, Object> t, String required) {
    if (!required.equals(t.get("status")))
      throw conflict("Trip must be " + required + " for this action");
  }

  private void crew(Map<String, Object> t) {
    var line = orders.lockLine(uuid(t, "order_item_id"));
    branches.requireActive(line.branchId());
    var v = fleet.requireTruck(uuid(t, "truck_id"), line.branchId(), today());
    fleet.requireDriver(uuid(t, "driver_id"), line.branchId(), today());
    if (t.get("loaded_quantity") != null
        && number(t, "loaded_quantity").compareTo(v.capacity()) > 0)
      throw conflict("Load exceeds truck capacity");
  }

  Map<String, Object> advance(UUID id, String action, TripDtos.Event r) {
    var t = lock(id);
    String body = payload();
    if (replay(id, r.eventId(), action, body)) return result(id, true);
    String next;
    switch (action) {
      case "START_LOADING" -> {
        state(t, "ALLOCATED");
        if (((java.sql.Date) t.get("planned_date")).toLocalDate().isAfter(today()))
          throw conflict("Trip cannot start before its planned date");
        crew(t);
        next = "LOADING";
      }
      case "DISPATCH" -> {
        state(t, "LOADED");
        crew(t);
        if (!t.get("source").equals("TRANSPORT"))
          supply.dispatch(id, number(t, "loaded_quantity"), "Trip " + id);
        orders.recordTripDispatch(id, uuid(t, "order_item_id"), number(t, "loaded_quantity"));
        next = "DISPATCHED";
        db.update("update trips set dispatched_at=current_timestamp where id=?", id);
      }
      case "ARRIVE" -> {
        state(t, "DISPATCHED");
        next = "ARRIVED";
      }
      case "START_UNLOADING" -> {
        state(t, "ARRIVED");
        next = "UNLOADING";
      }
      default -> throw new IllegalArgumentException("Unknown trip action");
    }
    db.update("update trips set status=? where id=?", next, id);
    event(id, r.eventId(), action, body);
    return result(id, false);
  }

  Map<String, Object> load(UUID id, TripDtos.Load r) {
    var t = lock(id);
    String body = payload(amount(r.gross()), amount(r.tare()), clean(r.slipReference()));
    if (replay(id, r.eventId(), "LOAD", body)) return result(id, true);
    state(t, "LOADING");
    crew(t);
    BigDecimal loaded = r.gross().subtract(r.tare());
    if (loaded.signum() <= 0)
      throw new IllegalArgumentException("Gross weight must exceed tare weight");
    if (loaded.compareTo(number(t, "planned_quantity")) > 0)
      throw conflict("Loaded weight exceeds planned quantity");
    db.update(
        "update trips set"
            + " loaded_gross=?,loaded_tare=?,loaded_quantity=?,loading_slip=?,loaded_at=current_timestamp,status='LOADED'"
            + " where id=?",
        r.gross(),
        r.tare(),
        loaded,
        r.slipReference().strip(),
        id);
    event(id, r.eventId(), "LOAD", body);
    return result(id, false);
  }

  Map<String, Object> deliver(UUID id, TripDtos.Deliver r) {
    var t = lock(id);
    String body =
        payload(
            amount(r.gross()),
            amount(r.tare()),
            amount(r.acceptedQuantity()),
            clean(r.slipReference()),
            clean(r.receiverName()),
            clean(r.proofReference()),
            clean(r.discrepancyReason()));
    if (replay(id, r.eventId(), "DELIVER", body)) return result(id, true);
    state(t, "UNLOADING");
    BigDecimal received = r.gross().subtract(r.tare());
    if (received.signum() <= 0 || r.acceptedQuantity().compareTo(received) > 0)
      throw new IllegalArgumentException(
          "Received net must be positive and accepted cannot exceed received");
    if (received.compareTo(number(t, "loaded_quantity")) > 0)
      throw conflict(
          "Received weight exceeds recorded load; resolve the weighbridge discrepancy first");
    if ((received.compareTo(number(t, "loaded_quantity")) != 0
            || r.acceptedQuantity().compareTo(received) != 0)
        && clean(r.discrepancyReason()) == null)
      throw new IllegalArgumentException("Weight differences require a reason");
    if (!t.get("source").equals("TRANSPORT"))
      supply.deliver(
          id,
          number(t, "loaded_quantity"),
          received,
          r.acceptedQuantity(),
          today(),
          r.slipReference().strip(),
          clean(r.discrepancyReason()));
    orders.recordTripDelivery(id, received);
    db.update(
        "update trips set"
            + " received_gross=?,received_tare=?,received_quantity=?,accepted_quantity=?,delivery_slip=?,receiver_name=?,proof_reference=?,discrepancy_reason=?,status='DELIVERED',delivered_at=current_timestamp"
            + " where id=?",
        r.gross(),
        r.tare(),
        received,
        r.acceptedQuantity(),
        r.slipReference().strip(),
        r.receiverName().strip(),
        r.proofReference().strip(),
        clean(r.discrepancyReason()),
        id);
    fleet.release(id);
    event(id, r.eventId(), "DELIVER", body);
    return result(id, false);
  }

  Map<String, Object> cancel(UUID id, TripDtos.Cancel r) {
    var t = lock(id);
    String body = payload(r.reason().strip());
    if (replay(id, r.eventId(), "CANCEL", body)) return result(id, true);
    if (!Set.of("ALLOCATED", "LOADING", "LOADED").contains(t.get("status")))
      throw conflict("Only a trip that has not dispatched may be cancelled");
    if (!t.get("source").equals("TRANSPORT")) supply.cancel(id);
    fleet.release(id);
    db.update(
        "update trips set status='CANCELLED',cancel_reason=? where id=?", r.reason().strip(), id);
    event(id, r.eventId(), "CANCEL", body);
    return result(id, false);
  }

  Map<String, Object> reassign(UUID id, TripDtos.Reassign r) {
    var t = lock(id);
    String body = payload(r.truckId(), r.driverId(), r.reason().strip());
    if (replay(id, r.eventId(), "REASSIGN", body)) return result(id, true);
    if (!Set.of("DISPATCHED", "ARRIVED", "UNLOADING").contains(t.get("status")))
      throw conflict("Crew can be reassigned only after dispatch and before delivery");
    var line = orders.lockLine(uuid(t, "order_item_id"));
    var vehicle = fleet.requireTruck(r.truckId(), line.branchId(), today());
    String driver = fleet.requireDriver(r.driverId(), line.branchId(), today());
    if (number(t, "loaded_quantity").compareTo(vehicle.capacity()) > 0)
      throw conflict("Load exceeds replacement truck capacity");
    fleet.release(id);
    db.update(
        "update trips set truck_id=?,driver_id=?,truck_registration=?,driver_name=? where id=?",
        r.truckId(),
        r.driverId(),
        vehicle.registration(),
        driver,
        id);
    fleet.assign(id, r.truckId(), r.driverId());
    event(id, r.eventId(), "REASSIGN", body);
    return result(id, false);
  }

  private Map<String, Object> correctionView(UUID id, boolean replay) {
    var rows = db.queryForList("select * from trip_weight_corrections where id=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Weight correction not found");
    var result = normalize(rows.getFirst());
    result.remove("create_payload");
    result.put("replayed", replay);
    result.put(
        "events",
        db
            .queryForList(
                "select id,action,reason,actor,occurred_at from trip_weight_correction_events where"
                    + " correction_id=? order by occurred_at,id",
                id)
            .stream()
            .map(this::normalize)
            .toList());
    return result;
  }

  @Transactional(readOnly = true)
  Map<String, Object> correction(UUID id) {
    return correctionView(id, false);
  }

  Map<String, Object> correct(UUID trip, TripDtos.WeightCorrection r) {
    lock(trip);
    String reason = clean(r.discrepancyReason());
    String body =
        payload(
            trip,
            amount(r.loadedGross()),
            amount(r.loadedTare()),
            amount(r.receivedGross()),
            amount(r.receivedTare()),
            amount(r.acceptedQuantity()),
            reason,
            r.reason().strip(),
            r.evidenceReference().strip());
    var prior =
        db.queryForList("select create_payload from trip_weight_corrections where id=?", r.correctionId());
    if (!prior.isEmpty()) {
      if (!body.equals(prior.getFirst().get("create_payload")))
        throw conflict("Correction ID used with different data");
      return correctionView(r.correctionId(), true);
    }
    var t = trip(trip, true);
    if (!"DELIVERED".equals(t.get("status")))
      throw conflict("Weight corrections require a delivered trip");
    BigDecimal loaded = r.loadedGross().subtract(r.loadedTare());
    BigDecimal received = r.receivedGross().subtract(r.receivedTare());
    if (loaded.signum() <= 0 || received.signum() <= 0 || r.acceptedQuantity().compareTo(received) > 0)
      throw new IllegalArgumentException(
          "Corrected net weights must be positive and accepted cannot exceed received");
    if (received.compareTo(loaded) > 0)
      throw conflict("Corrected received weight cannot exceed loaded weight");
    if (loaded.compareTo(number(t, "planned_quantity")) > 0)
      throw conflict("Corrected load exceeds planned quantity");
    if ((loaded.compareTo(received) != 0 || received.compareTo(r.acceptedQuantity()) != 0)
        && reason == null)
      throw new IllegalArgumentException("Weight differences require a reason");
    requireAcceptedCoversReturns(trip, r.acceptedQuantity());
    if (db.queryForObject(
            "select count(*) from trip_weight_correction_claims where trip_id=?",
            Integer.class,
            trip)
        > 0) throw conflict("A weight correction is already pending for this trip");
    db.update(
        "insert into"
            + " trip_weight_corrections(id,trip_id,loaded_gross,loaded_tare,loaded_quantity,received_gross,received_tare,received_quantity,accepted_quantity,discrepancy_reason,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        r.correctionId(),
        trip,
        r.loadedGross(),
        r.loadedTare(),
        loaded,
        r.receivedGross(),
        r.receivedTare(),
        received,
        r.acceptedQuantity(),
        reason,
        r.reason().strip(),
        r.evidenceReference().strip(),
        actor(),
        body);
    db.update(
        "insert into trip_weight_correction_claims(trip_id,correction_id) values(?,?)",
        trip,
        r.correctionId());
    audit.record("TRIP_WEIGHT_CORRECTION_SUBMITTED", "TRIP", trip, "Correction=" + r.correctionId());
    return correctionView(r.correctionId(), false);
  }

  Map<String, Object> decideCorrection(UUID id, TripDtos.Decision r, boolean approve) {
    var rows = db.queryForList("select * from trip_weight_corrections where id=? for update", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Weight correction not found");
    var record = normalize(rows.getFirst());
    String action = approve ? "APPROVE" : "REJECT";
    var old =
        db.queryForList("select * from trip_weight_correction_events where id=?", r.eventId());
    if (!old.isEmpty()) {
      var event = normalize(old.getFirst());
      if (!id.equals(event.get("correction_id"))
          || !action.equals(event.get("action"))
          || !r.reason().strip().equals(event.get("reason"))
          || !actor().equals(event.get("actor")))
        throw conflict("Review event ID used with different data or actor");
      return correctionView(id, true);
    }
    if (!"PENDING".equals(record.get("status"))) throw conflict("Weight correction already reviewed");
    if (actor().equals(record.get("submitted_by")))
      throw conflict("A different user must review the weight correction");
    UUID trip = uuid(record, "trip_id");
    if (approve) {
      var t = lock(trip);
      if (!"DELIVERED".equals(t.get("status")))
        throw conflict("Weight corrections require a delivered trip");
      BigDecimal loaded = number(record, "loaded_quantity");
      BigDecimal received = number(record, "received_quantity");
      BigDecimal accepted = number(record, "accepted_quantity");
      requireAcceptedCoversReturns(trip, accepted);
      BigDecimal capacity =
          db.queryForObject(
              "select capacity_tonnes from trucks where id=?",
              BigDecimal.class,
              uuid(t, "truck_id"));
      if (capacity == null || loaded.compareTo(capacity) > 0)
        throw conflict("Corrected load exceeds truck capacity");
      if (!"TRANSPORT".equals(t.get("source"))) {
        if ("YARD".equals(t.get("source")))
          supply.correctYardIssue(trip, loaded, r.eventId(), "Weight correction " + id);
        else
          supply.correctDirectReceipt(
              trip, loaded, received, accepted, (String) record.get("discrepancy_reason"));
      }
      orders.correctTripWeights(trip, loaded, received);
      db.update(
          "update trips set"
              + " loaded_gross=?,loaded_tare=?,loaded_quantity=?,received_gross=?,received_tare=?,received_quantity=?,accepted_quantity=?,discrepancy_reason=?"
              + " where id=?",
          record.get("loaded_gross"),
          record.get("loaded_tare"),
          loaded,
          record.get("received_gross"),
          record.get("received_tare"),
          received,
          accepted,
          record.get("discrepancy_reason"),
          trip);
    }
    db.update(
        "insert into trip_weight_correction_events(id,correction_id,action,reason,actor)"
            + " values(?,?,?,?,?)",
        r.eventId(),
        id,
        action,
        r.reason().strip(),
        actor());
    db.update(
        "update trip_weight_corrections set status=?,reviewed_by=?,reviewed_at=current_timestamp,review_reason=? where id=?",
        approve ? "APPROVED" : "REJECTED",
        actor(),
        r.reason().strip(),
        id);
    db.update("delete from trip_weight_correction_claims where correction_id=?", id);
    audit.record(
        approve ? "TRIP_WEIGHT_CORRECTION_APPROVED" : "TRIP_WEIGHT_CORRECTION_REJECTED",
        "TRIP",
        trip,
        "Event=" + r.eventId());
    return correctionView(id, false);
  }
}
