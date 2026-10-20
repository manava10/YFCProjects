package com.yfctransport.suppliers;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.materials.MaterialRegistry;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class SupplierService implements SupplierRegistry {
  private final SupplierRepository suppliers;
  private final PlantRepository plants;
  private final PlantMaterialRepository links;
  private final MaterialRegistry materials;
  private final AuditTrail audit;

  SupplierService(
      SupplierRepository suppliers,
      PlantRepository plants,
      PlantMaterialRepository links,
      MaterialRegistry materials,
      AuditTrail audit) {
    this.suppliers = suppliers;
    this.plants = plants;
    this.links = links;
    this.materials = materials;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  Supplier supplier(UUID id) {
    return suppliers
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
  }

  @Transactional(readOnly = true)
  Plant plant(UUID supplierId, UUID id) {
    supplier(supplierId);
    Plant p =
        plants
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plant not found"));
    if (!p.supplierId.equals(supplierId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plant not found for this supplier");
    return p;
  }

  @Override
  @Transactional(readOnly = true)
  public String name(UUID id) {
    return supplier(id).name;
  }

  @Override
  @Transactional(readOnly = true)
  public Source requireActiveSource(UUID supplierId, UUID plantId, UUID materialId) {
    requireSupplierActive(supplierId);
    Plant p = plant(supplierId, plantId);
    if (!p.active) throw new IllegalArgumentException("Plant is inactive");
    materials.getActive(materialId);
    if (links.findByPlantIdAndMaterialId(plantId, materialId).isEmpty())
      throw new IllegalArgumentException("Material is not supplied by this plant");
    return new Source(supplier(supplierId).name, p.name);
  }

  Supplier save(UUID id, SupplierController.WriteSupplier r) {
    Supplier s = id == null ? new Supplier() : supplier(id);
    s.code = r.code().toUpperCase(Locale.ROOT);
    s.name = r.name().strip();
    s.gstin = r.gstin();
    s.mobile = r.mobile().strip();
    s.email = r.email();
    s.address = r.address().strip();
    suppliers.save(s);
    audit.record(
        id == null ? "SUPPLIER_CREATED" : "SUPPLIER_UPDATED",
        "SUPPLIER",
        s.id,
        "Supplier master saved");
    return s;
  }

  Supplier status(UUID id, boolean active) {
    Supplier s = supplier(id);
    s.active = active;
    audit.record("SUPPLIER_STATUS_CHANGED", "SUPPLIER", id, "Active=" + active);
    return s;
  }

  private void requireSupplierActive(UUID id) {
    if (!supplier(id).active) throw new IllegalArgumentException("Supplier is inactive");
  }

  Plant savePlant(UUID supplierId, UUID id, SupplierController.WritePlant r) {
    requireSupplierActive(supplierId);
    Plant p = id == null ? new Plant() : plant(supplierId, id);
    p.supplierId = supplierId;
    p.code = r.code().toUpperCase(Locale.ROOT);
    p.name = r.name().strip();
    p.address = r.address().strip();
    p.contactName = r.contactName();
    p.mobile = r.mobile();
    plants.save(p);
    audit.record(
        id == null ? "PLANT_CREATED" : "PLANT_UPDATED", "PLANT", p.id, "Supplier plant saved");
    return p;
  }

  Plant plantStatus(UUID supplierId, UUID id, boolean active) {
    Plant p = plant(supplierId, id);
    if (active) requireSupplierActive(supplierId);
    p.active = active;
    audit.record("PLANT_STATUS_CHANGED", "PLANT", id, "Active=" + active);
    return p;
  }

  PlantMaterial attach(UUID supplierId, UUID plantId, UUID materialId) {
    requireSupplierActive(supplierId);
    if (!plant(supplierId, plantId).active) throw new IllegalArgumentException("Plant is inactive");
    materials.getActive(materialId);
    return links
        .findByPlantIdAndMaterialId(plantId, materialId)
        .orElseGet(
            () -> {
              PlantMaterial link = links.save(new PlantMaterial(plantId, materialId));
              audit.record("PLANT_MATERIAL_ADDED", "PLANT", plantId, "Material " + materialId);
              return link;
            });
  }

  void detach(UUID supplierId, UUID plantId, UUID materialId) {
    plant(supplierId, plantId);
    links
        .findByPlantIdAndMaterialId(plantId, materialId)
        .ifPresent(
            link -> {
              links.delete(link);
              audit.record("PLANT_MATERIAL_REMOVED", "PLANT", plantId, "Material " + materialId);
            });
  }

  @Transactional(readOnly = true)
  SupplierController.MaterialView materialView(PlantMaterial p) {
    var m = materials.get(p.materialId);
    return new SupplierController.MaterialView(p.materialId, m.name(), m.unit(), m.active());
  }
}
