package com.yfctransport.identity;

import com.yfctransport.audit.AuditTrail;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
      HttpSecurity http, AccountRepository accounts, AuditTrail audit, LoginProtection logins)
      throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
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
                          logins.succeeded(auth.getName());
                          audit.recordAs(
                              auth.getName(),
                              "LOGIN_SUCCEEDED",
                              "USER",
                              ((AccountPrincipal) auth.getPrincipal()).accountId,
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
                          if (auth != null)
                            audit.recordAs(auth.getName(), "LOGOUT", "USER", null, "Session ended");
                        })
                    .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
        .addFilterBefore(new LoginLockFilter(logins), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new SessionValidityFilter(accounts), AuthorizationFilter.class)
        .build();
  }
}
