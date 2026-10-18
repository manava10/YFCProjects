package com.yfctransport.platform;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
class CsrfController {
  record Token(String headerName, String parameterName, String token) {}

  @GetMapping("/api/v1/csrf")
  Token csrf(CsrfToken token) {
    return new Token(token.getHeaderName(), token.getParameterName(), token.getToken());
  }
}
