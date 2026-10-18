package com.yfctransport.suppliers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suppliers")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class SupplierController {
  private final SupplierRepository suppliers;
  private final PlantRepository plants;
  private final PlantMaterialRepository links;
  private final SupplierService service;

  SupplierController(
      SupplierRepository suppliers,
      PlantRepository plants,
      PlantMaterialRepository links,
      SupplierService service) {
    this.suppliers = suppliers;
    this.plants = plants;
    this.links = links;
    this.service = service;
  }

  record WriteSupplier(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Pattern(regexp = "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]") String gstin,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}") String mobile,
      @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 500) String address) {}

  record WritePlant(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 500) String address,
      @Size(max = 160) String contactName,
      @Pattern(regexp = "[+0-9 ()-]{7,20}") String mobile) {}

  record Status(@NotNull Boolean active) {}

  record View(
      UUID id,
      String code,
      String name,
      String gstin,
      String mobile,
      String email,
      String address,
      boolean active) {
    static View of(Supplier s) {
      return new View(s.id, s.code, s.name, s.gstin, s.mobile, s.email, s.address, s.active);
    }
  }

  record PlantView(
      UUID id,
      UUID supplierId,
      String code,
      String name,
      String address,
      String contactName,
      String mobile,
      boolean active) {
    static PlantView of(Plant p) {
      return new PlantView(
          p.id, p.supplierId, p.code, p.name, p.address, p.contactName, p.mobile, p.active);
    }
  }

  record MaterialView(UUID materialId, String name, String unit, boolean active) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  View create(@Valid @RequestBody WriteSupplier r) {
    return View.of(service.save(null, r));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  View update(@PathVariable UUID id, @Valid @RequestBody WriteSupplier r) {
    return View.of(service.save(id, r));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  View status(@PathVariable UUID id, @Valid @RequestBody Status r) {
    return View.of(service.status(id, r.active()));
  }

  @GetMapping("/{id}")
  View get(@PathVariable UUID id) {
    return View.of(service.supplier(id));
  }

  @GetMapping
  Page<View> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return suppliers.findAll(PageRequest.of(page, size, Sort.by("code"))).map(View::of);
  }

  @PostMapping("/{supplierId}/plants")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  PlantView createPlant(@PathVariable UUID supplierId, @Valid @RequestBody WritePlant r) {
    return PlantView.of(service.savePlant(supplierId, null, r));
  }

  @GetMapping("/{supplierId}/plants")
  Page<PlantView> listPlants(
      @PathVariable UUID supplierId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    service.supplier(supplierId);
    return plants
        .findBySupplierId(supplierId, PageRequest.of(page, size, Sort.by("code")))
        .map(PlantView::of);
  }

  @GetMapping("/{supplierId}/plants/{plantId}")
  PlantView getPlant(@PathVariable UUID supplierId, @PathVariable UUID plantId) {
    return PlantView.of(service.plant(supplierId, plantId));
  }

  @PutMapping("/{supplierId}/plants/{plantId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  PlantView updatePlant(
      @PathVariable UUID supplierId, @PathVariable UUID plantId, @Valid @RequestBody WritePlant r) {
    return PlantView.of(service.savePlant(supplierId, plantId, r));
  }

  @PatchMapping("/{supplierId}/plants/{plantId}/status")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  PlantView plantStatus(
      @PathVariable UUID supplierId, @PathVariable UUID plantId, @Valid @RequestBody Status r) {
    return PlantView.of(service.plantStatus(supplierId, plantId, r.active()));
  }

  @PutMapping("/{supplierId}/plants/{plantId}/materials/{materialId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  MaterialView attach(
      @PathVariable UUID supplierId, @PathVariable UUID plantId, @PathVariable UUID materialId) {
    return service.materialView(service.attach(supplierId, plantId, materialId));
  }

  @DeleteMapping("/{supplierId}/plants/{plantId}/materials/{materialId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  void detach(
      @PathVariable UUID supplierId, @PathVariable UUID plantId, @PathVariable UUID materialId) {
    service.detach(supplierId, plantId, materialId);
  }

  @GetMapping("/{supplierId}/plants/{plantId}/materials")
  Page<MaterialView> listMaterials(
      @PathVariable UUID supplierId,
      @PathVariable UUID plantId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    service.plant(supplierId, plantId);
    return links
        .findByPlantId(plantId, PageRequest.of(page, size, Sort.by("id")))
        .map(service::materialView);
  }
}
