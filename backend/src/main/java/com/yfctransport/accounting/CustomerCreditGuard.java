package com.yfctransport.accounting;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.orders.CustomerCreditCheck;
import java.math.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class CustomerCreditGuard implements CustomerCreditCheck {
  private final JdbcTemplate db;
  private final AuditTrail audit;

  CustomerCreditGuard(JdbcTemplate db, AuditTrail audit) {
    this.db = db;
    this.audit = audit;
  }

  @Override
  public void requireWithinLimit(
      UUID customerId, BigDecimal additionalExposure, boolean overrideRequested) {
    if (customerId == null) return;
    var rows =
        db.queryForList(
            "select account_id,credit_limit from party_financial_profiles where"
                + " party_type='CUSTOMER' and customer_id=?",
            customerId);
    if (rows.isEmpty()) return;
    var row = new LinkedHashMap<String, Object>();
    rows.getFirst().forEach((k, v) -> row.put(k.toLowerCase(Locale.ROOT), v));
    BigDecimal limit = (BigDecimal) row.get("credit_limit");
    if (limit == null) return;
    limit = limit.setScale(2, RoundingMode.HALF_UP);
    UUID accountId = (UUID) row.get("account_id");
    BigDecimal current =
        db.queryForObject(
                "select coalesce(sum(l.debit - l.credit), 0) from journal_lines l join journals j"
                    + " on j.id=l.journal_id where l.account_id=? and j.status='POSTED'",
                BigDecimal.class,
                accountId)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal added =
        additionalExposure == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : additionalExposure.setScale(2, RoundingMode.HALF_UP);
    BigDecimal projected = current.add(added);
    if (projected.compareTo(limit) <= 0) return;

    if (overrideRequested) {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      boolean isAdmin =
          auth != null
              && auth.getAuthorities().stream()
                  .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
      if (!isAdmin)
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only a SUPER_ADMIN can override a customer credit limit");
      audit.record(
          "CREDIT_LIMIT_OVERRIDDEN",
          "CUSTOMER",
          customerId,
          "Limit=" + limit + "; current=" + current + "; projected=" + projected);
      return;
    }

    throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Customer credit limit of "
            + limit
            + " INR would be exceeded (current receivable "
            + current
            + " INR, projected "
            + projected
            + " INR). Collect payment or request a SUPER_ADMIN override.");
  }
}
