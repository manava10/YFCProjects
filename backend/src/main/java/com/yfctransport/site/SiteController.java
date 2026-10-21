package com.yfctransport.site;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/site")
class SiteController {
  private final SiteService site;

  SiteController(SiteService site) {
    this.site = site;
  }

  @GetMapping
  SiteDtos.Catalog catalog() {
    return site.catalog(false);
  }

  @PostMapping("/enquiries")
  @ResponseStatus(HttpStatus.CREATED)
  SiteDtos.EnquiryReceipt enquire(@Valid @RequestBody SiteDtos.EnquiryInput input) {
    return site.enquire(input);
  }

  @PostMapping("/quotes")
  @ResponseStatus(HttpStatus.CREATED)
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
    return site.saveProfile(profile);
  }

  @PutMapping("/stats/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Stat stat(@PathVariable UUID id, @Valid @RequestBody SiteDtos.StatInput input) {
    return site.saveStat(id, input);
  }

  @PutMapping("/materials/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Material material(
      @PathVariable UUID id, @Valid @RequestBody SiteDtos.MaterialInput input) {
    return site.saveMaterial(id, input);
  }

  @PostMapping("/materials")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Material addMaterial(@Valid @RequestBody SiteDtos.NewMaterial input) {
    return site.addMaterial(input);
  }

  @PutMapping("/plants/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Plant plant(@PathVariable UUID id, @Valid @RequestBody SiteDtos.PlantInput input) {
    return site.savePlant(id, input);
  }

  @PostMapping("/plants")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Plant addPlant(@Valid @RequestBody SiteDtos.NewPlant input) {
    return site.addPlant(input);
  }

  @PutMapping("/destinations/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Destination destination(
      @PathVariable UUID id, @Valid @RequestBody SiteDtos.DestinationInput input) {
    return site.saveDestination(id, input);
  }

  @PostMapping("/destinations")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.Destination addDestination(@Valid @RequestBody SiteDtos.NewDestination input) {
    return site.addDestination(input);
  }

  @PutMapping("/freight")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  void freight(@Valid @RequestBody SiteDtos.FreightUpdate update) {
    site.saveFreight(update);
  }

  @PostMapping("/desk/quotes/{id}/stage")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SiteDtos.StaffQuote stage(@PathVariable UUID id, @Valid @RequestBody SiteDtos.StageInput input) {
    return site.advance(id, input.stage());
  }
}
