package com.yfctransport.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rehydrates authentication from the database-backed YFC_SESSION cookie when Tomcat's in-memory
 * HTTP session is missing (for example, after a server restart or when load-balanced across nodes).
 */
class PersistentSessionFilter extends OncePerRequestFilter {
  private final PersistentSessionStore sessions;
  private final HttpSessionSecurityContextRepository sessionRepo =
      new HttpSessionSecurityContextRepository();

  PersistentSessionFilter(PersistentSessionStore sessions) {
    this.sessions = sessions;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var current = SecurityContextHolder.getContext().getAuthentication();
    boolean alreadyAuthenticated =
        current != null
            && current.isAuthenticated()
            && !(current instanceof AnonymousAuthenticationToken);
    if (!alreadyAuthenticated) {
      sessions
          .resolve(request)
          .ifPresent(
              principal -> {
                var auth =
                    UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);
                sessionRepo.saveContext(context, request, response);
              });
    }
    chain.doFilter(request, response);
  }
}
