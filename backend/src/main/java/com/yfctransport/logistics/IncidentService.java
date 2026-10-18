package com.yfctransport.logistics;

import com.yfctransport.audit.AuditTrail;
import java.sql.Timestamp;
import java.time.Clock;
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
class IncidentService {
  private final JdbcTemplate db;
  private final TripExpenseContext trips;
  private final AuditTrail audit;
  private final ObjectMapper json;
  private final Clock clock;

  IncidentService(
      JdbcTemplate db, TripExpenseContext trips, AuditTrail audit, ObjectMapper json, Clock clock) {
    this.db = db;
    this.trips = trips;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private ResponseStatusException conflict() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Incident or event ID already used with different data, or incident already resolved");
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach(
        (key, value) -> {
          String name = key.toLowerCase(Locale.ROOT);
          if (!name.endsWith("_payload")) {
            StringBuilder camel = new StringBuilder();
            boolean upper = false;
            for (char c : name.toCharArray()) {
              if (c == '_') upper = true;
              else {
                camel.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
              }
            }
            result.put(camel.toString(), value instanceof Timestamp t ? t.toInstant() : value);
          }
        });
    return result;
  }

  Map<String, Object> report(UUID trip, IncidentController.Report r) {
    var context = trips.lock(trip);
    String payload = json.writeValueAsString(r);
    var existing =
        db.queryForList(
            "select create_payload,trip_id from trip_incidents where id=?", r.incidentId());
    if (!existing.isEmpty()) {
      var row = existing.getFirst();
      if (!payload.equals(row.get("create_payload")) || !trip.equals(row.get("trip_id")))
        throw conflict();
      var result = get(trip, r.incidentId());
      result.put("replayed", true);
      return result;
    }
    if (r.occurredAt().isAfter(clock.instant())
        || r.occurredAt().isBefore(context.createdAt())
        || (context.deliveredAt() != null && r.occurredAt().isAfter(context.deliveredAt())))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Occurrence must be within the trip's allocation-to-delivery window and not in the"
              + " future");
    if ("CANCELLED".equals(context.status()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cannot report a new incident against a cancelled trip");
    db.update(
        "insert into"
            + " trip_incidents(id,trip_id,category,occurred_at,description,evidence_reference,reported_by,create_payload)"
            + " values(?,?,?,?,?,?,?,?)",
        r.incidentId(),
        trip,
        r.category().name(),
        Timestamp.from(r.occurredAt()),
        r.description(),
        r.evidenceReference(),
        actor(),
        payload);
    audit.record("TRIP_INCIDENT_REPORTED", "TRIP_INCIDENT", r.incidentId(), "Trip=" + trip);
    var result = get(trip, r.incidentId());
    result.put("replayed", false);
    return result;
  }

  @Transactional(readOnly = true)
  Map<String, Object> get(UUID trip, UUID id) {
    var rows = db.queryForList("select * from trip_incidents where id=? and trip_id=?", id, trip);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found for this trip");
    return view(rows.getFirst());
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> list(UUID trip, int page, int size) {
    if (db.queryForObject("select count(*) from trips where id=?", Integer.class, trip) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    return db
        .queryForList(
            "select * from trip_incidents where trip_id=? order by reported_at,id limit ? offset ?",
            trip,
            size,
            (long) page * size)
        .stream()
        .map(this::view)
        .toList();
  }

  Map<String, Object> resolve(UUID trip, UUID id, IncidentController.Resolution r) {
    trips.lock(trip);
    var current = get(trip, id);
    String payload = json.writeValueAsString(r);
    var prior =
        db.queryForList(
            "select id,resolution_payload from trip_incidents where resolution_id=?", r.eventId());
    if (!prior.isEmpty()) {
      if (!id.equals(prior.getFirst().get("id"))
          || !payload.equals(prior.getFirst().get("resolution_payload"))) throw conflict();
      current.put("replayed", true);
      return current;
    }
    if (!"OPEN".equals(current.get("status"))) throw conflict();
    db.update(
        "update trip_incidents set"
            + " status='RESOLVED',resolution_id=?,resolution_payload=?,resolution=?,resolution_evidence=?,resolved_by=?,resolved_at=?"
            + " where id=?",
        r.eventId(),
        payload,
        r.resolution(),
        r.evidenceReference(),
        actor(),
        Timestamp.from(clock.instant()),
        id);
    audit.record(
        "TRIP_INCIDENT_RESOLVED", "TRIP_INCIDENT", id, "Trip=" + trip + "; event=" + r.eventId());
    var result = get(trip, id);
    result.put("replayed", false);
    return result;
  }
}
