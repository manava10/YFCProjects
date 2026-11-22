package com.yfctransport.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces per-IP rate limits on public and authentication endpoints, returning HTTP 429 with
 * standard Retry-After and X-RateLimit-* headers when exceeded.
 */
class PublicRateLimitFilter extends OncePerRequestFilter {
  private final PublicRateLimiter limiter;

  PublicRateLimitFilter(PublicRateLimiter limiter) {
    this.limiter = limiter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!limiter.isEnabled()) {
      chain.doFilter(request, response);
      return;
    }
    String path =
        request.getServletPath() == null || request.getServletPath().isBlank()
            ? request.getRequestURI()
            : request.getServletPath();
    String method = request.getMethod();
    String scope = null;
    int limit = 0;

    if ("POST".equalsIgnoreCase(method) && path.endsWith("/login")) {
      scope = "LOGIN";
      limit = limiter.loginLimit();
    } else if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/auth/password-reset")) {
      scope = "AUTH_RESET";
      limit = limiter.authLimit();
    } else if ("POST".equalsIgnoreCase(method)
        && (path.equals("/api/v1/account-applications") || path.equals("/api/v1/site/enquiries"))) {
      scope = "PUBLIC_WRITE:" + path;
      limit = limiter.publicWriteLimit();
    } else if ("GET".equalsIgnoreCase(method)
        && (path.equals("/api/v1/site") || path.startsWith("/api/v1/site/quotes/"))) {
      scope = "PUBLIC_READ";
      limit = limiter.publicReadLimit();
    }

    if (scope != null) {
      String ip = clientIp(request);
      var decision = limiter.check(scope + "|" + ip, limit);
      response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
      response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
      if (!decision.allowed()) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response
            .getWriter()
            .write(
                "{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded."
                    + " Please wait "
                    + decision.retryAfterSeconds()
                    + " seconds before retrying.\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private String clientIp(HttpServletRequest request) {
    if (limiter.isTrustProxy()) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].strip();
      }
    }
    return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
  }
}
