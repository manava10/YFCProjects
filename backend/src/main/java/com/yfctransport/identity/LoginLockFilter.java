package com.yfctransport.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

class LoginLockFilter extends OncePerRequestFilter {
  private final LoginProtection protection;

  LoginLockFilter(LoginProtection protection) {
    this.protection = protection;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path =
        request.getServletPath() == null || request.getServletPath().isBlank()
            ? request.getRequestURI()
            : request.getServletPath();
    if ("POST".equalsIgnoreCase(request.getMethod())
        && path.endsWith("/login")
        && protection.locked(request.getParameter("username"))) {
      response.sendError(401, "Too many failed sign-in attempts. Try again later.");
      return;
    }
    chain.doFilter(request, response);
  }
}
