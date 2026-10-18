package com.yfctransport.customers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/projects")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class LocationController {
  private final CustomerProjectRepository projects;
  private final DeliverySiteRepository sites;
  private final LocationService service;

  LocationController(
      CustomerProjectRepository projects, DeliverySiteRepository sites, LocationService service) {
    this.projects = projects;
    this.sites = sites;
    this.service = service;
  }

  record CreateProject(
      @NotNull UUID branchId,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotBlank @Size(max = 160) String name,
      @Size(max = 500) String description) {}

  record UpdateProject(
      @NotBlank @Size(max = 160) String name, @Size(max = 500) String description) {}

  record WriteSite(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 500) String address,
      @Size(max = 160) String contactName,
      @Pattern(regexp = "[+0-9 ()-]{7,20}") String mobile) {}

  record Status(@NotNull Boolean active) {}

  record ProjectView(
      UUID id,
      UUID customerId,
      UUID branchId,
      String code,
      String name,
      String description,
      boolean active) {
    static ProjectView of(CustomerProject p) {
      return new ProjectView(
          p.id, p.customerId, p.branchId, p.code, p.name, p.description, p.active);
    }
  }

  record SiteView(
      UUID id,
      UUID projectId,
      String code,
      String name,
      String address,
      String contactName,
      String mobile,
      boolean active) {
    static SiteView of(DeliverySite s) {
      return new SiteView(
          s.id, s.projectId, s.code, s.name, s.address, s.contactName, s.mobile, s.active);
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ProjectView create(@PathVariable UUID customerId, @Valid @RequestBody CreateProject r) {
    return ProjectView.of(service.createProject(customerId, r));
  }

  @GetMapping
  Page<ProjectView> list(
      @PathVariable UUID customerId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    service.customer(customerId);
    return projects
        .findByCustomerId(customerId, PageRequest.of(page, size, Sort.by("code")))
        .map(ProjectView::of);
  }

  @GetMapping("/{projectId}")
  ProjectView get(@PathVariable UUID customerId, @PathVariable UUID projectId) {
    return ProjectView.of(service.project(customerId, projectId));
  }

  @PutMapping("/{projectId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ProjectView update(
      @PathVariable UUID customerId,
      @PathVariable UUID projectId,
      @Valid @RequestBody UpdateProject r) {
    return ProjectView.of(service.updateProject(customerId, projectId, r));
  }

  @PatchMapping("/{projectId}/status")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ProjectView projectStatus(
      @PathVariable UUID customerId, @PathVariable UUID projectId, @Valid @RequestBody Status r) {
    return ProjectView.of(service.projectStatus(customerId, projectId, r.active()));
  }

  @PostMapping("/{projectId}/sites")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteView createSite(
      @PathVariable UUID customerId,
      @PathVariable UUID projectId,
      @Valid @RequestBody WriteSite r) {
    return SiteView.of(service.saveSite(customerId, projectId, null, r));
  }

  @GetMapping("/{projectId}/sites")
  Page<SiteView> listSites(
      @PathVariable UUID customerId,
      @PathVariable UUID projectId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    service.project(customerId, projectId);
    return sites
        .findByProjectId(projectId, PageRequest.of(page, size, Sort.by("code")))
        .map(SiteView::of);
  }

  @GetMapping("/{projectId}/sites/{siteId}")
  SiteView getSite(
      @PathVariable UUID customerId, @PathVariable UUID projectId, @PathVariable UUID siteId) {
    return SiteView.of(service.site(customerId, projectId, siteId));
  }

  @PutMapping("/{projectId}/sites/{siteId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteView updateSite(
      @PathVariable UUID customerId,
      @PathVariable UUID projectId,
      @PathVariable UUID siteId,
      @Valid @RequestBody WriteSite r) {
    return SiteView.of(service.saveSite(customerId, projectId, siteId, r));
  }

  @PatchMapping("/{projectId}/sites/{siteId}/status")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteView siteStatus(
      @PathVariable UUID customerId,
      @PathVariable UUID projectId,
      @PathVariable UUID siteId,
      @Valid @RequestBody Status r) {
    return SiteView.of(service.siteStatus(customerId, projectId, siteId, r.active()));
  }
}
