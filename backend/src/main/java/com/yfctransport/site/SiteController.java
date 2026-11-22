package com.yfctransport.site;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/site")
class SiteController {
  private static final long CACHE_TTL_MS = 60_000L;
  private final SiteService site;
  private volatile CachedCatalog cached;

  private record CachedCatalog(SiteDtos.Catalog value, long expiresAt) {}

  SiteController(SiteService site) {
    this.site = site;
  }

  private void evict() {
    this.cached = null;
  }

  @GetMapping
  SiteDtos.Catalog catalog() {
    long now = System.currentTimeMillis();
    CachedCatalog current = this.cached;
    if (current != null && now < current.expiresAt()) return current.value();
    SiteDtos.Catalog fresh = site.catalog(false);
    this.cached = new CachedCatalog(fresh, now + CACHE_TTL_MS);
    return fresh;
  }

  @PostMapping("/enquiries")
  @ResponseStatus(HttpStatus.CREATED)
  SiteDtos.EnquiryReceipt enquire(@Valid @RequestBody SiteDtos.EnquiryInput input) {
    return site.enquire(input);
  }

  @PostMapping("/quotes")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.PublicQuote quote(@Valid @RequestBody SiteDtos.QuoteInput input) {
    return site.quote(input);
  }

  @GetMapping("/quotes/{reference}")
  SiteDtos.PublicQuote track(@PathVariable String reference) {
    return site.track(reference);
  }

  @GetMapping("/desk")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Desk desk() {
    return site.desk();
  }

  @PutMapping("/profile")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Profile profile(@Valid @RequestBody SiteDtos.Profile profile) {
    var result = site.saveProfile(profile);
    evict();
    return result;
  }

  @PutMapping("/stats/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Stat stat(@PathVariable UUID id, @Valid @RequestBody SiteDtos.StatInput input) {
    var result = site.saveStat(id, input);
    evict();
    return result;
  }

  @PutMapping("/materials/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Material material(
      @PathVariable UUID id, @Valid @RequestBody SiteDtos.MaterialInput input) {
    var result = site.saveMaterial(id, input);
    evict();
    return result;
  }

  @PostMapping("/materials")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Material addMaterial(@Valid @RequestBody SiteDtos.NewMaterial input) {
    var result = site.addMaterial(input);
    evict();
    return result;
  }

  @PutMapping("/plants/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Plant plant(@PathVariable UUID id, @Valid @RequestBody SiteDtos.PlantInput input) {
    var result = site.savePlant(id, input);
    evict();
    return result;
  }

  @PostMapping("/plants")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Plant addPlant(@Valid @RequestBody SiteDtos.NewPlant input) {
    var result = site.addPlant(input);
    evict();
    return result;
  }

  @PutMapping("/destinations/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Destination destination(
      @PathVariable UUID id, @Valid @RequestBody SiteDtos.DestinationInput input) {
    var result = site.saveDestination(id, input);
    evict();
    return result;
  }

  @PostMapping("/destinations")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Destination addDestination(@Valid @RequestBody SiteDtos.NewDestination input) {
    var result = site.addDestination(input);
    evict();
    return result;
  }

  @PutMapping("/freight")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  void freight(@Valid @RequestBody SiteDtos.FreightUpdate update) {
    site.saveFreight(update);
    evict();
  }

  @PostMapping("/desk/quotes/{id}/stage")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.StaffQuote stage(@PathVariable UUID id, @Valid @RequestBody SiteDtos.StageInput input) {
    return site.advance(id, input.stage());
  }
}
