package com.yfctransport.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
class AuthenticationController {
  private final AccountService service;

  AuthenticationController(AccountService service) {
    this.service = service;
  }

  record SessionView(String username, java.util.List<String> roles) {}

  @GetMapping("/me")
  SessionView me(Authentication auth) {
    return new SessionView(
        auth.getName(), auth.getAuthorities().stream().map(a -> a.getAuthority()).toList());
  }

  record CompleteReset(
      @NotBlank @Size(max = 100) String token,
      @NotBlank @Size(min = 12, max = 72) String newPassword) {}

  @PostMapping("/password-reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void reset(@Valid @RequestBody CompleteReset r) {
    service.reset(r.token(), r.newPassword());
  }
}
