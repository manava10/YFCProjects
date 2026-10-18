package com.yfctransport.branches;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class BranchController {
  private final BranchRepository branches;
  private final BranchService service;

  BranchController(BranchRepository branches, BranchService service) {
    this.branches = branches;
    this.service = service;
  }

  record WriteBranch(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 500) String address,
      @NotBlank @Size(max = 100) String city,
      @NotBlank @Size(max = 100) String state,
      @NotBlank @Pattern(regexp = "[1-9][0-9]{5}") String pincode) {}

  record Status(@NotNull Boolean active) {}

  record View(
      UUID id,
      String code,
      String name,
      String address,
      String city,
      String state,
      String pincode,
      boolean active) {
    static View of(Branch a) {
      return new View(a.id, a.code, a.name, a.address, a.city, a.state, a.pincode, a.active);
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  View create(@Valid @RequestBody WriteBranch r) {
    return View.of(service.save(null, r));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  View update(@PathVariable UUID id, @Valid @RequestBody WriteBranch r) {
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
    return branches.findAll(PageRequest.of(page, size, Sort.by("code"))).map(View::of);
  }
}
