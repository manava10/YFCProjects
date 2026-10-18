package com.yfctransport.materials;

import com.yfctransport.audit.AuditTrail;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class MaterialService implements MaterialRegistry {
  private final MaterialRepository materials;
  private final AuditTrail audit;

  MaterialService(MaterialRepository materials, AuditTrail audit) {
    this.materials = materials;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  Material find(UUID id) {
    return materials
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));
  }

  @Transactional(readOnly = true)
  public Snapshot getActive(UUID id) {
    Material a = find(id);
    if (!a.active) throw new IllegalArgumentException("Material is inactive");
    return new Snapshot(a.id, a.name, a.unit, a.active);
  }

  @Transactional(readOnly = true)
  public Snapshot get(UUID id) {
    Material a = find(id);
    return new Snapshot(a.id, a.name, a.unit, a.active);
  }

  Material save(UUID id, MaterialController.WriteMaterial r) {
    Material a = id == null ? new Material() : find(id);
    a.code = r.code().strip().toUpperCase(Locale.ROOT);
    a.name = r.name().strip();
    a.category = r.category().name();
    a.unit = "TONNE";
    a.hsnCode = r.hsnCode();
    materials.save(a);
    audit.record(
        id == null ? "MATERIAL_CREATED" : "MATERIAL_UPDATED",
        "MATERIAL",
        a.id,
        "Material master saved");
    return a;
  }

  Material status(UUID id, boolean active) {
    Material a = find(id);
    a.active = active;
    audit.record("MATERIAL_STATUS_CHANGED", "MATERIAL", id, "Active=" + active);
    return a;
  }
}
