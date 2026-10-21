package com.yfctransport.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account-applications")
class AccountApplicationController {
  record Input(
      @NotNull UUID applicationId,
      @NotNull @Pattern(regexp = "CUSTOMER|SUPPLIER|DRIVER") String role,
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]{2,79}") String username,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}") String mobile,
      @Pattern(regexp = "[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][A-Za-z0-9][Zz][A-Za-z0-9]") String gstin,
      @Size(max = 500) String address,
      @Size(max = 254) String email,
      @Pattern(regexp = "[A-Za-z0-9-]{5,40}") String licenseNumber,
      LocalDate licenseExpiry,
      @Size(max = 160) String emergencyContact,
      @Pattern(regexp = "[+0-9 ()-]{7,20}") String emergencyMobile) {}

  record Decision(
      @NotNull UUID eventId, @NotBlank @Size(max = 1000) String reason, UUID branchId) {}

  private final AccountApplicationService service;

  AccountApplicationController(AccountApplicationService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody Input r) {
    var result = service.submit(r);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .body(result);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  List<Map<String, Object>> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(status, page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> approve(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decide(id, r, true);
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  Map<String, Object> reject(@PathVariable UUID id, @Valid @RequestBody Decision r) {
    return service.decide(id, r, false);
  }
}
