package com.yfctransport.identity;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rechecks account version on every request so disabled/changed accounts lose existing sessions.
 */
class SessionValidityFilter extends OncePerRequestFilter {
  private final AccountRepository accounts;

  SessionValidityFilter(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
      var account = accounts.findById(principal.accountId);
      if (account.isEmpty()
          || !account.get().enabled
          || account.get().authVersion != principal.authVersion) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        response.sendError(401, "Session is no longer valid");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
