package com.yfctransport.customers;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.branches.BranchRegistry;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class LocationService implements CustomerLocations {
  private final CustomerRepository customers;
  private final CustomerProjectRepository projects;
  private final DeliverySiteRepository sites;
  private final BranchRegistry branches;
  private final AuditTrail audit;

  LocationService(
      CustomerRepository customers,
      CustomerProjectRepository projects,
      DeliverySiteRepository sites,
      BranchRegistry branches,
      AuditTrail audit) {
    this.customers = customers;
    this.projects = projects;
    this.sites = sites;
    this.branches = branches;
    this.audit = audit;
  }

  void customer(UUID id) {
    if (!customers.existsById(id))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
  }

  @Transactional(readOnly = true)
  CustomerProject project(UUID customerId, UUID id) {
    customer(customerId);
    CustomerProject p =
        projects
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    if (!p.customerId.equals(customerId))
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Project not found for this customer");
    return p;
  }

  @Transactional(readOnly = true)
  DeliverySite site(UUID customerId, UUID projectId, UUID id) {
    project(customerId, projectId);
    DeliverySite s =
        sites
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found"));
    if (!s.projectId.equals(projectId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found for this project");
    return s;
  }

  CustomerProject createProject(UUID customerId, LocationController.CreateProject r) {
    customer(customerId);
    branches.requireActive(r.branchId());
    CustomerProject p = new CustomerProject();
    p.customerId = customerId;
    p.branchId = r.branchId();
    p.code = r.code().toUpperCase(Locale.ROOT);
    p.name = r.name().strip();
    p.description = r.description();
    projects.save(p);
    audit.record("PROJECT_CREATED", "PROJECT", p.id, "Customer project created");
    return p;
  }

  CustomerProject updateProject(UUID customerId, UUID id, LocationController.UpdateProject r) {
    CustomerProject p = project(customerId, id);
    p.name = r.name().strip();
    p.description = r.description();
    audit.record("PROJECT_UPDATED", "PROJECT", p.id, "Project details updated");
    return p;
  }

  CustomerProject projectStatus(UUID customerId, UUID id, boolean active) {
    CustomerProject p = project(customerId, id);
    if (active) branches.requireActive(p.branchId);
    p.active = active;
    audit.record("PROJECT_STATUS_CHANGED", "PROJECT", id, "Active=" + active);
    return p;
  }

  DeliverySite saveSite(UUID customerId, UUID projectId, UUID id, LocationController.WriteSite r) {
    CustomerProject p = project(customerId, projectId);
    requireProjectActive(p);
    DeliverySite s = id == null ? new DeliverySite() : site(customerId, projectId, id);
    s.projectId = projectId;
    s.code = r.code().toUpperCase(Locale.ROOT);
    s.name = r.name().strip();
    s.address = r.address().strip();
    s.contactName = r.contactName();
    s.mobile = r.mobile();
    sites.save(s);
    audit.record(
        id == null ? "SITE_CREATED" : "SITE_UPDATED", "DELIVERY_SITE", s.id, "Delivery site saved");
    return s;
  }

  DeliverySite siteStatus(UUID customerId, UUID projectId, UUID id, boolean active) {
    DeliverySite s = site(customerId, projectId, id);
    if (active) requireProjectActive(project(customerId, projectId));
    s.active = active;
    audit.record("SITE_STATUS_CHANGED", "DELIVERY_SITE", id, "Active=" + active);
    return s;
  }

  void requireProjectActive(CustomerProject p) {
    if (!p.active) throw new IllegalArgumentException("Project is inactive");
    branches.requireActive(p.branchId);
  }

  @Transactional(readOnly = true)
  public Location getActive(UUID customerId, UUID siteId) {
    DeliverySite s =
        sites
            .findById(siteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found"));
    CustomerProject p = project(customerId, s.projectId);
    requireProjectActive(p);
    if (!s.active) throw new IllegalArgumentException("Delivery site is inactive");
    return new Location(s.id, p.id, p.branchId, s.name, s.address);
  }
}
