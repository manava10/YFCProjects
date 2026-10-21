package com.yfctransport.suppliers;

import com.yfctransport.audit.AuditTrail;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupplierOpeningService implements SupplierOpening {
  private final SupplierRepository suppliers;
  private final PlantRepository plants;
  private final JdbcTemplate db;
  private final AuditTrail audit;

  SupplierOpeningService(
      SupplierRepository suppliers, PlantRepository plants, JdbcTemplate db, AuditTrail audit) {
    this.suppliers = suppliers;
    this.plants = plants;
    this.db = db;
    this.audit = audit;
  }

  @Override
  @Transactional
  public UUID open(String name, String gstin, String mobile, String email, String address) {
    if (db.queryForObject("select count(*) from suppliers where gstin=?", Integer.class, gstin) > 0)
      throw new IllegalArgumentException("A supplier with this GSTIN is already registered");
    String code = null;
    for (int attempt = 0; attempt < 5 && code == null; attempt++) {
      String candidate =
          ("S" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
              .toUpperCase(Locale.ROOT);
      if (db.queryForObject("select count(*) from suppliers where code=?", Integer.class, candidate)
          == 0) code = candidate;
    }
    if (code == null) throw new IllegalStateException("Could not allocate a supplier code");
    Supplier s = new Supplier();
    s.code = code;
    s.name = name.strip();
    s.gstin = gstin;
    s.mobile = mobile.strip();
    s.email = email;
    s.address = address.strip();
    suppliers.save(s);
    Plant p = new Plant();
    p.supplierId = s.id;
    p.code = "MAIN";
    p.name = "Main plant";
    p.address = address.strip();
    p.contactName = name.strip();
    p.mobile = mobile.strip();
    plants.save(p);
    audit.record("SUPPLIER_CREATED", "SUPPLIER", s.id, "Opened from an approved account application");
    audit.record("PLANT_CREATED", "PLANT", p.id, "Main plant opened with the supplier application");
    return s.id;
  }
}
