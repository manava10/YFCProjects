package com.yfctransport.orders;

import com.yfctransport.branches.BranchRegistry;
import com.yfctransport.customers.CustomerLocations;
import com.yfctransport.materials.MaterialRegistry;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class OrderPreparation {
  private final BranchRegistry branches;
  private final CustomerLocations locations;
  private final MaterialRegistry materials;

  OrderPreparation(
      BranchRegistry branches, CustomerLocations locations, MaterialRegistry materials) {
    this.branches = branches;
    this.locations = locations;
    this.materials = materials;
  }

  record Prepared(
      String material,
      String address,
      UUID branchId,
      UUID projectId,
      UUID siteId,
      UUID materialId) {}

  Prepared prepare(OrderController.CreateOrder r) {
    UUID branchId = r.branchId(), projectId = r.projectId();
    String material = r.material(), address = r.deliverySite();
    if (r.materialId() != null) {
      var m = materials.getActive(r.materialId());
      if (material != null && !material.isBlank() && !material.strip().equals(m.name()))
        throw new IllegalArgumentException("Material name does not match materialId");
      material = m.name();
    }
    if (r.deliverySiteId() != null) {
      var site = locations.getActive(r.customerId(), r.deliverySiteId());
      if (branchId != null && !branchId.equals(site.branchId()))
        throw new IllegalArgumentException("Branch does not match the delivery site's project");
      if (projectId != null && !projectId.equals(site.projectId()))
        throw new IllegalArgumentException("Project does not match the delivery site");
      if (address != null && !address.isBlank() && !address.strip().equals(site.address()))
        throw new IllegalArgumentException("Delivery address does not match deliverySiteId");
      branchId = site.branchId();
      projectId = site.projectId();
      address = site.address();
    } else if (projectId != null)
      throw new IllegalArgumentException("deliverySiteId is required with projectId");
    if (branchId != null) branches.requireActive(branchId);
    if (material == null || material.isBlank())
      throw new IllegalArgumentException("Provide materialId or a material description");
    if (address == null || address.isBlank())
      throw new IllegalArgumentException("Provide deliverySiteId or a delivery address");
    return new Prepared(
        material.strip(), address.strip(), branchId, projectId, r.deliverySiteId(), r.materialId());
  }
}
