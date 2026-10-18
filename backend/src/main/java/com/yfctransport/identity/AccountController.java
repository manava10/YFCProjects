package com.yfctransport.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AccountController {
  private final AccountService service;
  private final AccountRepository accounts;

  AccountController(AccountService service, AccountRepository accounts) {
    this.service = service;
    this.accounts = accounts;
  }

  record CreateUser(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]{2,79}") String username,
      @NotBlank @Size(max = 120) String displayName,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotNull Role role) {}

  record UpdateAccess(@NotNull Role role, @NotNull Boolean enabled) {}

  record UserView(
      UUID id, String username, String displayName, Role role, boolean enabled, Instant createdAt) {
    static UserView of(Account a) {
      return new UserView(a.id, a.username, a.displayName, a.role, a.enabled, a.createdAt);
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  UserView create(@Valid @RequestBody CreateUser r) {
    return UserView.of(service.create(r.username(), r.displayName(), r.password(), r.role()));
  }

  @GetMapping
  Page<UserView> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return accounts.findAll(PageRequest.of(page, size, Sort.by("username"))).map(UserView::of);
  }

  @PatchMapping("/{id}/access")
  UserView access(@PathVariable UUID id, @Valid @RequestBody UpdateAccess r) {
    return UserView.of(service.update(id, r.role(), r.enabled()));
  }

  @PostMapping("/{id}/password-reset")
  ResponseEntity<AccountService.ResetIssued> reset(@PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issueReset(id));
  }
}
