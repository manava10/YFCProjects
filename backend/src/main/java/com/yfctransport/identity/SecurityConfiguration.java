package com.yfctransport.identity;

import com.yfctransport.audit.AuditTrail;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      AccountRepository accounts,
      AuditTrail audit,
      LoginProtection logins,
      PersistentSessionStore persistentSessions,
      PublicRateLimiter rateLimiter)
      throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/account-applications")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/site")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/site/enquiries")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/site/quotes/*")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/v1/csrf",
                        "/api/v1/auth/password-reset")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                        HttpStatus.UNAUTHORIZED),
                    r -> r.getRequestURI().startsWith("/api/")))
        .formLogin(
            f ->
                f.successHandler(
                        (request, response, auth) -> {
                          String portal = request.getParameter("portal");
                          if (portal != null && !portal.isBlank()) {
                            boolean isStaff =
                                auth.getAuthorities().stream()
                                    .map(a -> a.getAuthority())
                                    .anyMatch(
                                        r ->
                                            "ROLE_SUPER_ADMIN".equals(r)
                                                || "ROLE_OPERATIONS".equals(r)
                                                || "ROLE_ACCOUNTS".equals(r));
                            boolean isExternal =
                                auth.getAuthorities().stream()
                                    .map(a -> a.getAuthority())
                                    .anyMatch(
                                        r ->
                                            "ROLE_CUSTOMER".equals(r)
                                                || "ROLE_SUPPLIER".equals(r)
                                                || "ROLE_DRIVER".equals(r));
                            boolean staffPortal = "STAFF".equalsIgnoreCase(portal);
                            boolean customerPortal = "CUSTOMER".equalsIgnoreCase(portal);
                            if ((staffPortal && !isStaff) || (customerPortal && !isExternal)) {
                              org.springframework.security.core.context.SecurityContextHolder
                                  .clearContext();
                              var session = request.getSession(false);
                              if (session != null) session.invalidate();
                              String msg =
                                  staffPortal
                                      ? "This is a customer or partner account. Please sign in on the Customer Portal (/signin)."
                                      : "This is an office staff account. Please sign in on the Staff ERP portal (/staff).";
                              response.setStatus(HttpStatus.FORBIDDEN.value());
                              response.setContentType("application/json");
                              response.getWriter().write("{\"detail\":\"" + msg + "\"}");
                              return;
                            }
                          }
                          logins.succeeded(auth.getName());
                          var principal = (AccountPrincipal) auth.getPrincipal();
                          persistentSessions.issue(principal, request, response);
                          audit.recordAs(
                              auth.getName(),
                              "LOGIN_SUCCEEDED",
                              "USER",
                              principal.accountId,
                              "Session authenticated");
                          response.setStatus(204);
                        })
                    .failureHandler(
                        (request, response, error) -> {
                          logins.failed(request.getParameter("username"));
                          audit.recordAs(
                              "ANONYMOUS", "LOGIN_FAILED", "USER", null, "Authentication failed");
                          response.sendError(401, "Invalid credentials");
                        }))
        .logout(
            l ->
                l.addLogoutHandler(
                        (request, response, auth) -> {
                          persistentSessions.revoke(request, response);
                          if (auth != null)
                            audit.recordAs(auth.getName(), "LOGOUT", "USER", null, "Session ended");
                        })
                    .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
        .headers(
            h ->
                h.referrerPolicy(
                        r ->
                            r.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicyHeader(
                        p -> p.policy("camera=(), microphone=(), geolocation=()")))
        .addFilterBefore(new PublicRateLimitFilter(rateLimiter), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new LoginLockFilter(logins), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new PersistentSessionFilter(persistentSessions), AnonymousAuthenticationFilter.class)
        .addFilterBefore(
            new SessionValidityFilter(accounts, persistentSessions), AuthorizationFilter.class)
        .build();
  }
}
