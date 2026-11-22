package com.yfctransport.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class ReportsService {
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private final JdbcTemplate db;

  ReportsService(JdbcTemplate db) {
    this.db = db;
  }

  private Map<String, Object> norm(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private static BigDecimal money(Object v) {
    if (v == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal d = v instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(v));
    return d.setScale(2, RoundingMode.HALF_UP);
  }

  Map<String, Object> profitAndLoss(LocalDate from, LocalDate to, UUID branchId) {
    if (from.isAfter(to))
      throw new IllegalArgumentException("Start date must not be after end date");
    String sql =
        "select a.id, a.code, a.name, a.type, coalesce(sum(l.debit),0) as debit,"
            + " coalesce(sum(l.credit),0) as credit"
            + " from journal_lines l"
            + " join journals j on j.id = l.journal_id"
            + " join ledger_accounts a on a.id = l.account_id"
            + " where j.status = 'POSTED'"
            + " and j.entry_date >= ? and j.entry_date <= ?"
            + " and a.type in ('INCOME','EXPENSE')"
            + (branchId == null ? "" : " and j.branch_id = ?")
            + " group by a.id, a.code, a.name, a.type"
            + " order by a.code";
    var args = new ArrayList<Object>();
    args.add(from);
    args.add(to);
    if (branchId != null) args.add(branchId);

    var income = new ArrayList<Map<String, Object>>();
    var expenses = new ArrayList<Map<String, Object>>();
    BigDecimal totalIncome = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalExpense = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    for (var raw : db.queryForList(sql, args.toArray())) {
      var row = norm(raw);
      BigDecimal debit = money(row.get("debit"));
      BigDecimal credit = money(row.get("credit"));
      String type = String.valueOf(row.get("type"));
      if ("INCOME".equals(type)) {
        BigDecimal amount = credit.subtract(debit);
        row.put("amount", amount);
        income.add(row);
        totalIncome = totalIncome.add(amount);
      } else {
        BigDecimal amount = debit.subtract(credit);
        row.put("amount", amount);
        expenses.add(row);
        totalExpense = totalExpense.add(amount);
      }
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("from", from);
    result.put("to", to);
    result.put("branchId", branchId);
    result.put("currency", "INR");
    result.put("incomeAccounts", income);
    result.put("expenseAccounts", expenses);
    result.put("totalIncome", totalIncome);
    result.put("totalExpense", totalExpense);
    result.put("netProfit", totalIncome.subtract(totalExpense));
    return result;
  }

  Map<String, Object> balanceSheet(LocalDate asOf, UUID branchId) {
    String sql =
        "select a.id, a.code, a.name, a.type, coalesce(sum(l.debit),0) as debit,"
            + " coalesce(sum(l.credit),0) as credit"
            + " from journal_lines l"
            + " join journals j on j.id = l.journal_id"
            + " join ledger_accounts a on a.id = l.account_id"
            + " where j.status = 'POSTED' and j.entry_date <= ?"
            + (branchId == null ? "" : " and j.branch_id = ?")
            + " group by a.id, a.code, a.name, a.type"
            + " order by a.code";
    var args = new ArrayList<Object>();
    args.add(asOf);
    if (branchId != null) args.add(branchId);

    var assets = new ArrayList<Map<String, Object>>();
    var liabilities = new ArrayList<Map<String, Object>>();
    var equity = new ArrayList<Map<String, Object>>();
    BigDecimal totalAssets = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalLiabilities = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalEquity = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal retainedEarnings = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    for (var raw : db.queryForList(sql, args.toArray())) {
      var row = norm(raw);
      BigDecimal debit = money(row.get("debit"));
      BigDecimal credit = money(row.get("credit"));
      String type = String.valueOf(row.get("type"));
      switch (type) {
        case "ASSET" -> {
          BigDecimal bal = debit.subtract(credit);
          row.put("balance", bal);
          assets.add(row);
          totalAssets = totalAssets.add(bal);
        }
        case "LIABILITY" -> {
          BigDecimal bal = credit.subtract(debit);
          row.put("balance", bal);
          liabilities.add(row);
          totalLiabilities = totalLiabilities.add(bal);
        }
        case "EQUITY" -> {
          BigDecimal bal = credit.subtract(debit);
          row.put("balance", bal);
          equity.add(row);
          totalEquity = totalEquity.add(bal);
        }
        case "INCOME" -> retainedEarnings = retainedEarnings.add(credit.subtract(debit));
        case "EXPENSE" -> retainedEarnings = retainedEarnings.subtract(debit.subtract(credit));
        default -> {}
      }
    }

    BigDecimal totalEquityWithEarnings = totalEquity.add(retainedEarnings);
    BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquityWithEarnings);

    var result = new LinkedHashMap<String, Object>();
    result.put("asOf", asOf);
    result.put("branchId", branchId);
    result.put("currency", "INR");
    result.put("assets", assets);
    result.put("liabilities", liabilities);
    result.put("equity", equity);
    result.put("retainedEarnings", retainedEarnings);
    result.put("totalAssets", totalAssets);
    result.put("totalLiabilities", totalLiabilities);
    result.put("totalEquity", totalEquityWithEarnings);
    result.put("totalLiabilitiesAndEquity", totalLiabilitiesAndEquity);
    result.put("balanced", totalAssets.compareTo(totalLiabilitiesAndEquity) == 0);
    return result;
  }

  Map<String, Object> gstr1(LocalDate from, LocalDate to, UUID branchId) {
    if (from.isAfter(to))
      throw new IllegalArgumentException("Start date must not be after end date");
    String invSql =
        "select p.id as preparation_id, t.invoice_number, p.prepared_on,"
            + " b.code as branch_code, b.name as branch_name, b.gstin as branch_gstin,"
            + " b.state as branch_state, b.state_code as branch_state_code,"
            + " p.party_name as customer_name, c.gstin as customer_gstin,"
            + " t.supply, t.taxable_amount, t.cgst_amount, t.sgst_amount,"
            + " t.igst_amount, t.tax_total, t.payable_total"
            + " from billing_preparations p"
            + " join billing_tax_assessments t on t.preparation_id = p.id"
            + " join branches b on b.id = p.branch_id"
            + " left join customers c on c.id = p.party_id"
            + " where p.kind = 'SALES_ORDER' and p.status = 'READY'"
            + " and t.invoice_number is not null"
            + " and p.prepared_on >= ? and p.prepared_on <= ?"
            + (branchId == null ? "" : " and p.branch_id = ?")
            + " order by p.prepared_on asc, t.invoice_number asc";
    var args = new ArrayList<Object>();
    args.add(from);
    args.add(to);
    if (branchId != null) args.add(branchId);

    var invoices = new ArrayList<Map<String, Object>>();
    BigDecimal totalTaxable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalCgst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalSgst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalIgst = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalTax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalPayable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    for (var raw : db.queryForList(invSql, args.toArray())) {
      var row = norm(raw);
      String custGstin = (String) row.get("customer_gstin");
      row.put("category", custGstin != null && !custGstin.isBlank() ? "B2B" : "B2C");
      invoices.add(row);
      totalTaxable = totalTaxable.add(money(row.get("taxable_amount")));
      totalCgst = totalCgst.add(money(row.get("cgst_amount")));
      totalSgst = totalSgst.add(money(row.get("sgst_amount")));
      totalIgst = totalIgst.add(money(row.get("igst_amount")));
      totalTax = totalTax.add(money(row.get("tax_total")));
      totalPayable = totalPayable.add(money(row.get("payable_total")));
    }

    String hsnSql =
        "select tl.hsn_or_sac, tl.component, tl.rate_percent,"
            + " count(*) as line_count,"
            + " coalesce(sum(tl.taxable_amount),0) as taxable_amount,"
            + " coalesce(sum(tl.cgst_amount),0) as cgst_amount,"
            + " coalesce(sum(tl.sgst_amount),0) as sgst_amount,"
            + " coalesce(sum(tl.igst_amount),0) as igst_amount"
            + " from billing_tax_lines tl"
            + " join billing_tax_assessments t on t.preparation_id = tl.preparation_id"
            + " join billing_preparations p on p.id = tl.preparation_id"
            + " where p.kind = 'SALES_ORDER' and p.status = 'READY'"
            + " and t.invoice_number is not null"
            + " and p.prepared_on >= ? and p.prepared_on <= ?"
            + (branchId == null ? "" : " and p.branch_id = ?")
            + " group by tl.hsn_or_sac, tl.component, tl.rate_percent"
            + " order by tl.hsn_or_sac asc, tl.rate_percent asc";
    var hsnSummary =
        db.queryForList(hsnSql, args.toArray()).stream()
            .map(
                raw -> {
                  var row = norm(raw);
                  BigDecimal lineTax =
                      money(row.get("cgst_amount"))
                          .add(money(row.get("sgst_amount")))
                          .add(money(row.get("igst_amount")));
                  row.put("tax_total", lineTax);
                  return row;
                })
            .toList();

    // GSTR-1 Table 9B: Approved Sales Credit Notes & Debit Notes (CDNR / CDNUR)
    String notesSql =
        "select n.id, n.note_type, n.note_number, n.note_date, n.original_invoice_number,"
            + " p.prepared_on as original_invoice_date, n.reason_code,"
            + " b.code as branch_code, b.gstin as branch_gstin,"
            + " n.party_name as customer_name, c.gstin as customer_gstin,"
            + " n.supply, n.hsn_or_sac, n.gst_rate_percent,"
            + " n.taxable_amount, n.cgst_amount, n.sgst_amount, n.igst_amount,"
            + " n.tax_total, n.total_amount"
            + " from gst_adjustment_notes n"
            + " join billing_preparations p on p.id = n.original_preparation_id"
            + " join branches b on b.id = n.branch_id"
            + " left join customers c on c.id = n.party_id"
            + " where n.kind = 'SALES_ORDER' and n.status = 'APPROVED'"
            + " and n.note_date >= ? and n.note_date <= ?"
            + (branchId == null ? "" : " and n.branch_id = ?")
            + " order by n.note_date asc, n.note_number asc";
    var creditDebitNotes = new ArrayList<Map<String, Object>>();
    BigDecimal noteNetTaxable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal noteNetTax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal noteNetPayable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    for (var raw : db.queryForList(notesSql, args.toArray())) {
      var row = norm(raw);
      String custGstin = (String) row.get("customer_gstin");
      row.put("category", custGstin != null && !custGstin.isBlank() ? "CDNR" : "CDNUR");
      creditDebitNotes.add(row);
      BigDecimal sign = "CREDIT_NOTE".equals(row.get("note_type")) ? BigDecimal.ONE.negate() : BigDecimal.ONE;
      noteNetTaxable = noteNetTaxable.add(money(row.get("taxable_amount")).multiply(sign));
      noteNetTax = noteNetTax.add(money(row.get("tax_total")).multiply(sign));
      noteNetPayable = noteNetPayable.add(money(row.get("total_amount")).multiply(sign));
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("from", from);
    result.put("to", to);
    result.put("branchId", branchId);
    result.put("currency", "INR");
    result.put("invoiceCount", invoices.size());
    result.put("noteCount", creditDebitNotes.size());
    result.put("totalTaxable", totalTaxable);
    result.put("totalCgst", totalCgst);
    result.put("totalSgst", totalSgst);
    result.put("totalIgst", totalIgst);
    result.put("totalTax", totalTax);
    result.put("totalPayable", totalPayable);
    result.put("netTaxable", totalTaxable.add(noteNetTaxable));
    result.put("netTax", totalTax.add(noteNetTax));
    result.put("netPayable", totalPayable.add(noteNetPayable));
    result.put("invoices", invoices);
    result.put("hsnSummary", hsnSummary);
    result.put("creditDebitNotes", creditDebitNotes);
    return result;
  }

  Map<String, Object> truckProfitability(LocalDate from, LocalDate to) {
    if (from.isAfter(to))
      throw new IllegalArgumentException("Start date must not be after end date");
    Instant fromTs = from.atStartOfDay(IST).toInstant();
    Instant toExclusiveTs = to.plusDays(1).atStartOfDay(IST).toInstant();

    var trucks =
        db.queryForList(
                "select t.id, t.registration, t.operational_status, b.code as branch_code, b.name as branch_name"
                    + " from trucks t left join branches b on b.id = t.branch_id"
                    + " order by t.registration asc")
            .stream()
            .map(this::norm)
            .toList();

    var tripStats = new HashMap<UUID, Map<String, Object>>();
    for (var raw :
        db.queryForList(
            "select truck_id, count(*) as trips_completed,"
                + " coalesce(sum(accepted_quantity),0) as tonnes_delivered,"
                + " coalesce(sum(distance_km),0) as total_km"
                + " from trips where status = 'DELIVERED'"
                + " and planned_date >= ? and planned_date <= ?"
                + " group by truck_id",
            from,
            to)) {
      var row = norm(raw);
      tripStats.put((UUID) row.get("truck_id"), row);
    }

    var fuelStats = new HashMap<UUID, Map<String, Object>>();
    for (var raw :
        db.queryForList(
            "select truck_id, coalesce(sum(quantity_litres),0) as fuel_litres,"
                + " coalesce(sum(amount),0) as fuel_cost"
                + " from fuel_entries where status = 'APPROVED'"
                + " and filled_on >= ? and filled_on <= ?"
                + " group by truck_id",
            from,
            to)) {
      var row = norm(raw);
      fuelStats.put((UUID) row.get("truck_id"), row);
    }

    var expenseStats = new HashMap<UUID, BigDecimal>();
    for (var raw :
        db.queryForList(
            "select truck_id, coalesce(sum(amount),0) as expense_cost"
                + " from operating_expenses where status = 'APPROVED'"
                + " and occurred_at >= ? and occurred_at < ?"
                + " group by truck_id",
            Timestamp.from(fromTs),
            Timestamp.from(toExclusiveTs))) {
      var row = norm(raw);
      expenseStats.put((UUID) row.get("truck_id"), money(row.get("expense_cost")));
    }

    var maintStats = new HashMap<UUID, BigDecimal>();
    for (var raw :
        db.queryForList(
            "select truck_id, coalesce(sum(cost_inr),0) as maint_cost"
                + " from fleet_maintenance_logs"
                + " where service_date >= ? and service_date <= ?"
                + " group by truck_id",
            from,
            to)) {
      var row = norm(raw);
      maintStats.put((UUID) row.get("truck_id"), money(row.get("maint_cost")));
    }

    var rows = new ArrayList<Map<String, Object>>();
    long grandTrips = 0;
    BigDecimal grandTonnes = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    BigDecimal grandKm = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    BigDecimal grandFuel = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal grandExpenses = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal grandMaintenance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    for (var truck : trucks) {
      UUID tid = (UUID) truck.get("id");
      var ts = tripStats.get(tid);
      var fs = fuelStats.get(tid);
      long completed = ts == null ? 0L : ((Number) ts.get("trips_completed")).longValue();
      BigDecimal tonnes =
          ts == null
              ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
              : ((BigDecimal) ts.get("tonnes_delivered")).setScale(3, RoundingMode.HALF_UP);
      BigDecimal km =
          ts == null
              ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
              : ((BigDecimal) ts.get("total_km")).setScale(3, RoundingMode.HALF_UP);
      BigDecimal fuelLitres =
          fs == null
              ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
              : ((BigDecimal) fs.get("fuel_litres")).setScale(3, RoundingMode.HALF_UP);
      BigDecimal fuelCost = fs == null ? money(null) : money(fs.get("fuel_cost"));
      BigDecimal roadExpenses = expenseStats.getOrDefault(tid, money(null));
      BigDecimal maintenanceCost = maintStats.getOrDefault(tid, money(null));
      BigDecimal totalCost = fuelCost.add(roadExpenses).add(maintenanceCost);
      BigDecimal costPerTonne =
          tonnes.signum() > 0
              ? totalCost.divide(tonnes, 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
      BigDecimal costPerKm =
          km.signum() > 0
              ? totalCost.divide(km, 2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

      var item = new LinkedHashMap<String, Object>();
      item.put("truckId", tid);
      item.put("registration", truck.get("registration"));
      item.put("branch", truck.get("branch_name") != null ? truck.get("branch_name") : "Unassigned");
      item.put("status", truck.get("operational_status"));
      item.put("tripsCompleted", completed);
      item.put("tonnesDelivered", tonnes);
      item.put("totalKm", km);
      item.put("fuelLitres", fuelLitres);
      item.put("fuelCost", fuelCost);
      item.put("roadExpenses", roadExpenses);
      item.put("maintenanceCost", maintenanceCost);
      item.put("totalOperatingCost", totalCost);
      item.put("costPerTonne", costPerTonne);
      item.put("costPerKm", costPerKm);
      rows.add(item);

      grandTrips += completed;
      grandTonnes = grandTonnes.add(tonnes);
      grandKm = grandKm.add(km);
      grandFuel = grandFuel.add(fuelCost);
      grandExpenses = grandExpenses.add(roadExpenses);
      grandMaintenance = grandMaintenance.add(maintenanceCost);
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("from", from);
    result.put("to", to);
    result.put("currency", "INR");
    result.put("totalTrips", grandTrips);
    result.put("totalTonnes", grandTonnes);
    result.put("totalKm", grandKm);
    result.put("totalFuelCost", grandFuel);
    result.put("totalRoadExpenses", grandExpenses);
    result.put("totalMaintenanceCost", grandMaintenance);
    result.put("totalOperatingCost", grandFuel.add(grandExpenses).add(grandMaintenance));
    result.put("trucks", rows);
    return result;
  }
}
