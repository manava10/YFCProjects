package com.yfctransport.materials;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/materials")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class MaterialController {
  private final MaterialRepository materials;
  private final MaterialService service;

  MaterialController(MaterialRepository materials, MaterialService service) {
    this.materials = materials;
    this.service = service;
  }

  enum Category {
    AGGREGATES,
    SAND,
    DUST,
    ROAD_MATERIALS,
    OTHER
  }

  record WriteMaterial(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotBlank @Size(max = 160) String name,
      @NotNull Category category,
      @Pattern(regexp = "[0-9]{4}|[0-9]{6}|[0-9]{8}") String hsnCode) {}

  record Status(@NotNull Boolean active) {}

  record View(
      UUID id,
      String code,
      String name,
      String category,
      String unit,
      String hsnCode,
      boolean active) {
    static View of(Material a) {
      return new View(a.id, a.code, a.name, a.category, a.unit, a.hsnCode, a.active);
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  View create(@Valid @RequestBody WriteMaterial r) {
    return View.of(service.save(null, r));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  View update(@PathVariable UUID id, @Valid @RequestBody WriteMaterial r) {
    return View.of(service.save(id, r));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  View status(@PathVariable UUID id, @Valid @RequestBody Status r) {
    return View.of(service.status(id, r.active()));
  }

  @GetMapping("/{id}")
  View get(@PathVariable UUID id) {
    return View.of(service.find(id));
  }

  @GetMapping
  Page<View> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return materials.findAll(PageRequest.of(page, size, Sort.by("code"))).map(View::of);
  }
}
