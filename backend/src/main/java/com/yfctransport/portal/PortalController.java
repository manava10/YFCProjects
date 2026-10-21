package com.yfctransport.portal;

import com.yfctransport.identity.ExternalAccounts;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portal")
@PreAuthorize("hasAnyRole('CUSTOMER','SUPPLIER','DRIVER')")
class PortalController {
  private final JdbcTemplate db;
  private final ExternalAccounts accounts;

  PortalController(JdbcTemplate db, ExternalAccounts accounts) {
    this.db = db;
    this.accounts = accounts;
  }

  private Map<String, Object> norm(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private List<Map<String, Object>> rows(String sql, Object... args) {
    return db.queryForList(sql, args).stream().map(this::norm).toList();
  }

  @GetMapping
  Map<String, Object> home() {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    UUID accountId = accounts.idOf(username);
    var links =
        db.queryForList("select role,record_id from account_ownership where account_id=?", accountId);
    if (links.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No records are linked to this login");
    var link = norm(links.getFirst());
    String role = (String) link.get("role");
    UUID record = (UUID) link.get("record_id");
    var result = new LinkedHashMap<String, Object>();
    result.put("role", role);
    result.put("record_id", record);
    if (role.equals("CUSTOMER")) {
      result.put(
          "profile",
          rows("select id,name,mobile,gstin from customers where id=?", record).getFirst());
      result.put(
          "orders",
          rows(
              "select id,material,business_type,ordered_quantity,created_at from sales_orders where"
                  + " customer_id=? order by created_at desc,id limit 50",
              record));
      result.put(
          "trips",
          rows(
              "select t.id,t.status,t.planned_date,t.planned_quantity,i.material from trips t join"
                  + " sales_order_items i on i.id=t.order_item_id join sales_orders o on"
                  + " o.id=i.order_id where o.customer_id=? order by t.planned_date desc,t.id limit"
                  + " 50",
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
              "select t.id,t.status,t.planned_date,t.planned_quantity,i.material from trips t join"
                  + " sales_order_items i on i.id=t.order_item_id where t.driver_id=? order by"
                  + " t.planned_date desc,t.id limit 50",
              record));
    }
    return result;
  }

  private List<Map<String, Object>> bills(UUID party, String kind) {
    return rows(
        "select p.id,p.kind,p.prepared_on,p.subtotal,p.status,t.invoice_number,t.payable_total from"
            + " billing_preparations p left join billing_tax_assessments t on"
            + " t.preparation_id=p.id where p.party_id=? and p.kind=? order by p.prepared_on desc,p.id"
            + " limit 50",
        party,
        kind);
  }
}
