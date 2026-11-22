package com.yfctransport.alerts;

import com.yfctransport.audit.AuditTrail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Aggregates live enterprise operational risks (E-Way Bill expiry, truck compliance renewals,
 * driver licences, preventive maintenance due, and overdue customer receivables), persists daily
 * digest snapshots, and optionally pushes JSON payloads to an external webhook (WhatsApp / Slack /
 * Email automation).
 */
@Service
@Transactional
class AlertDigestService {
  private final JdbcTemplate db;
  private final AuditTrail audit;
  private final Clock clock;
  private final ObjectMapper json;
  private final String webhookUrl;
  private final HttpClient http;

  AlertDigestService(
      JdbcTemplate db,
      AuditTrail audit,
      Clock clock,
      ObjectMapper json,
      @Value("${yfc.alerts.webhook-url:}") String webhookUrl) {
    this.db = db;
    this.audit = audit;
    this.clock = clock;
    this.json = json;
    this.webhookUrl = webhookUrl == null ? "" : webhookUrl.strip();
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  private Map<String, Object> norm(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private LocalDate toDate(Object v) {
    if (v == null) return null;
    if (v instanceof LocalDate d) return d;
    if (v instanceof java.sql.Date d) return d.toLocalDate();
    return LocalDate.parse(v.toString());
  }

  private Instant toInstant(Object v) {
    if (v == null) return null;
    if (v instanceof Instant i) return i;
    if (v instanceof Timestamp t) return t.toInstant();
    if (v instanceof OffsetDateTime o) return o.toInstant();
    return Instant.parse(v.toString());
  }

  @Transactional(readOnly = true)
  Map<String, Object> summary(
      int ewayHours, int complianceDays, int maintenanceKmMargin, int overdueDays) {
    Instant now = Instant.now(clock);
    LocalDate today = LocalDate.now(clock);
    LocalDate complianceCutoff = today.plusDays(complianceDays);
    LocalDate overdueCutoff = today.minusDays(overdueDays);

    int critical = 0;
    int warning = 0;

    // 1. Expiring / expired E-Way Bills on active trips
    var ewayAlerts = new ArrayList<Map<String, Object>>();
    Instant ewayHorizon = now.plus(ewayHours, ChronoUnit.HOURS);
    for (var raw :
        db.queryForList(
            "select id, truck_registration, driver_name, status, eway_bill_number,"
                + " eway_bill_valid_until from trips"
                + " where eway_bill_number is not null"
                + " and status not in ('DELIVERED','CANCELLED')"
                + " and eway_bill_valid_until <= ?"
                + " order by eway_bill_valid_until asc limit 100",
            Timestamp.from(ewayHorizon))) {
      var row = norm(raw);
      Instant valid = toInstant(row.get("eway_bill_valid_until"));
      boolean expired = valid != null && valid.isBefore(now);
      boolean urgent = expired || (valid != null && valid.isBefore(now.plus(6, ChronoUnit.HOURS)));
      row.put("eway_bill_valid_until", valid);
      row.put("expired", expired);
      row.put("severity", urgent ? "CRITICAL" : "WARNING");
      if (urgent) critical++;
      else warning++;
      ewayAlerts.add(row);
    }

    // 2. Truck Compliance Renewals (Insurance / Fitness / Permit / Pollution)
    var truckCompliance = new ArrayList<Map<String, Object>>();
    for (var raw :
        db.queryForList(
            "select t.id, t.registration, t.operational_status, b.name as branch_name,"
                + " t.insurance_expiry, t.fitness_expiry, t.permit_expiry, t.pollution_expiry"
                + " from trucks t left join branches b on b.id = t.branch_id"
                + " where t.operational_status = 'ACTIVE'"
                + " and (t.insurance_expiry <= ? or t.fitness_expiry <= ?"
                + " or t.permit_expiry <= ? or t.pollution_expiry <= ?)"
                + " order by t.registration asc limit 100",
            complianceCutoff,
            complianceCutoff,
            complianceCutoff,
            complianceCutoff)) {
      var row = norm(raw);
      var reasons = new ArrayList<String>();
      boolean expired = false;
      for (var entry :
          List.of(
              Map.entry("Insurance", toDate(row.get("insurance_expiry"))),
              Map.entry("Fitness", toDate(row.get("fitness_expiry"))),
              Map.entry("Permit", toDate(row.get("permit_expiry"))),
              Map.entry("PUC", toDate(row.get("pollution_expiry"))))) {
        LocalDate d = entry.getValue();
        if (d != null && !d.isAfter(complianceCutoff)) {
          if (d.isBefore(today)) {
            expired = true;
            reasons.add(entry.getKey() + " EXPIRED (" + d + ")");
          } else {
            reasons.add(entry.getKey() + " due " + d);
          }
        }
      }
      row.put("reasons", reasons);
      row.put("expired", expired);
      row.put("severity", expired ? "CRITICAL" : "WARNING");
      if (expired) critical++;
      else warning++;
      truckCompliance.add(row);
    }

    // 3. Driver Licence Renewals
    var driverLicences = new ArrayList<Map<String, Object>>();
    for (var raw :
        db.queryForList(
            "select d.id, d.name, d.mobile, d.license_number, d.license_expiry,"
                + " b.name as branch_name from drivers d"
                + " left join branches b on b.id = d.branch_id"
                + " where d.active = true and d.license_expiry <= ?"
                + " order by d.license_expiry asc limit 100",
            complianceCutoff)) {
      var row = norm(raw);
      LocalDate exp = toDate(row.get("license_expiry"));
      boolean expired = exp != null && exp.isBefore(today);
      row.put("expired", expired);
      row.put("severity", expired ? "CRITICAL" : "WARNING");
      if (expired) critical++;
      else warning++;
      driverLicences.add(row);
    }

    // 4. Preventive Maintenance Due (by date or odometer km)
    var maintenanceDue = new ArrayList<Map<String, Object>>();
    var seenMaint = new HashSet<String>();
    for (var raw :
        db.queryForList(
            "select m.id, m.truck_id, t.registration as truck_registration,"
                + " t.current_odometer_km, b.name as branch_name, m.category,"
                + " m.service_date, m.next_due_date, m.next_due_odometer_km"
                + " from fleet_maintenance_logs m"
                + " join trucks t on t.id = m.truck_id"
                + " left join branches b on b.id = t.branch_id"
                + " where (m.next_due_date is not null or m.next_due_odometer_km is not null)"
                + " order by m.service_date desc, m.created_at desc")) {
      var row = norm(raw);
      String key = row.get("truck_id") + ":" + row.get("category");
      if (!seenMaint.add(key)) continue;
      LocalDate nextDate = toDate(row.get("next_due_date"));
      Integer nextKm =
          row.get("next_due_odometer_km") instanceof Number n ? n.intValue() : null;
      int currentKm =
          row.get("current_odometer_km") instanceof Number n ? n.intValue() : 0;
      boolean overdue =
          (nextDate != null && nextDate.isBefore(today))
              || (nextKm != null && currentKm >= nextKm);
      boolean soon =
          (nextDate != null && !nextDate.isAfter(complianceCutoff))
              || (nextKm != null && currentKm + maintenanceKmMargin >= nextKm);
      if (overdue || soon) {
        row.put("overdue", overdue);
        row.put("km_remaining", nextKm == null ? null : nextKm - currentKm);
        row.put("severity", overdue ? "CRITICAL" : "WARNING");
        if (overdue) critical++;
        else warning++;
        maintenanceDue.add(row);
      }
    }

    // 5. Overdue Customer Receivables
    var overdueReceivables = new ArrayList<Map<String, Object>>();
    BigDecimal totalOverdueInr = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    for (var raw :
        db.queryForList(
            "select p.id, t.invoice_number, p.party_name, p.prepared_on, b.name as branch_name,"
                + " t.payable_total,"
                + " coalesce((select sum(a.amount) from invoice_allocations a"
                + " where a.preparation_id = p.id and a.status = 'ACTIVE'), 0) as paid_amount"
                + " from billing_preparations p"
                + " join billing_tax_assessments t on t.preparation_id = p.id"
                + " join branches b on b.id = p.branch_id"
                + " where p.kind = 'SALES_ORDER' and p.status = 'READY'"
                + " and t.payable_total is not null and p.prepared_on <= ?"
                + " order by p.prepared_on asc limit 100",
            overdueCutoff)) {
      var row = norm(raw);
      BigDecimal payable = ((BigDecimal) row.get("payable_total")).setScale(2, RoundingMode.HALF_UP);
      BigDecimal paid = ((BigDecimal) row.get("paid_amount")).setScale(2, RoundingMode.HALF_UP);
      BigDecimal outstanding = payable.subtract(paid);
      if (outstanding.signum() <= 0) continue;
      LocalDate prepDate = toDate(row.get("prepared_on"));
      long ageDays = prepDate == null ? 0 : ChronoUnit.DAYS.between(prepDate, today);
      boolean severe = ageDays >= Math.max(60, overdueDays * 2L);
      row.put("outstanding_amount", outstanding);
      row.put("age_days", ageDays);
      row.put("severity", severe ? "CRITICAL" : "WARNING");
      if (severe) critical++;
      else warning++;
      totalOverdueInr = totalOverdueInr.add(outstanding);
      overdueReceivables.add(row);
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("generatedAt", now);
    result.put("asOfDate", today);
    result.put("criticalCount", critical);
    result.put("warningCount", warning);
    result.put("totalAlerts", critical + warning);
    result.put("totalOverdueReceivablesInr", totalOverdueInr);
    result.put("ewayBillAlerts", ewayAlerts);
    result.put("truckComplianceAlerts", truckCompliance);
    result.put("driverLicenceAlerts", driverLicences);
    result.put("maintenanceDueAlerts", maintenanceDue);
    result.put("overdueReceivables", overdueReceivables);
    return result;
  }

  Map<String, Object> runDigest(String triggeredBy) {
    String actor = triggeredBy;
    if (actor == null || actor.isBlank()) {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      actor = auth != null ? auth.getName() : "SCHEDULER";
    }
    var summary = summary(24, 30, 1000, 30);
    int critical = (Integer) summary.get("criticalCount");
    int warning = (Integer) summary.get("warningCount");
    int total = (Integer) summary.get("totalAlerts");
    String payload = json.writeValueAsString(summary);

    String channel = "IN_APP";
    String dispatchStatus = "RECORDED";
    if (!webhookUrl.isBlank()) {
      channel = "WEBHOOK";
      try {
        HttpRequest req =
            HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
        dispatchStatus =
            res.statusCode() >= 200 && res.statusCode() < 300
                ? "DELIVERED"
                : "HTTP_" + res.statusCode();
      } catch (Exception ex) {
        dispatchStatus = "FAILED";
      }
    }

    UUID id = UUID.randomUUID();
    LocalDate today = LocalDate.now(clock);
    db.update(
        "insert into alert_digests(id,digest_date,critical_count,warning_count,total_alerts,"
            + " dispatched_channel,dispatch_status,summary_json,triggered_by)"
            + " values(?,?,?,?,?,?,?,?,?)",
        id,
        today,
        critical,
        warning,
        total,
        channel,
        dispatchStatus,
        payload,
        actor);
    audit.recordAs(
        actor,
        "ALERT_DIGEST_GENERATED",
        "ALERT_DIGEST",
        id,
        "Critical=" + critical + ", Warning=" + warning + ", Channel=" + channel + "/" + dispatchStatus);

    var response = new LinkedHashMap<String, Object>();
    response.put("id", id);
    response.put("digest_date", today);
    response.put("critical_count", critical);
    response.put("warning_count", warning);
    response.put("total_alerts", total);
    response.put("dispatched_channel", channel);
    response.put("dispatch_status", dispatchStatus);
    response.put("triggered_by", actor);
    response.put("summary", summary);
    return response;
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> listDigests(int limit) {
    return db
        .queryForList(
            "select id, digest_date, critical_count, warning_count, total_alerts,"
                + " dispatched_channel, dispatch_status, triggered_by, created_at"
                + " from alert_digests order by created_at desc limit ?",
            limit)
        .stream()
        .map(this::norm)
        .toList();
  }
}
