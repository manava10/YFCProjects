package com.yfctransport.accounting;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/reports")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
class ReportsController {
  private final ReportsService reports;

  ReportsController(ReportsService reports) {
    this.reports = reports;
  }

  @GetMapping("/profit-and-loss")
  Map<String, Object> profitAndLoss(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID branchId) {
    return reports.profitAndLoss(from, to, branchId);
  }

  @GetMapping("/balance-sheet")
  Map<String, Object> balanceSheet(
      @RequestParam LocalDate asOf, @RequestParam(required = false) UUID branchId) {
    return reports.balanceSheet(asOf, branchId);
  }

  @GetMapping("/gstr1")
  Map<String, Object> gstr1(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID branchId) {
    return reports.gstr1(from, to, branchId);
  }

  @GetMapping("/truck-profitability")
  Map<String, Object> truckProfitability(
      @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return reports.truckProfitability(from, to);
  }
}
