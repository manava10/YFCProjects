package com.yfctransport.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/H2-backed persistent session store so authenticated sessions survive backend restarts
 * and multi-instance deployments. Raw cookie tokens are never persisted; only SHA-256 hashes are
 * stored in persistent_sessions.
 */
@Service
class PersistentSessionStore {
  static final String COOKIE_NAME = "YFC_SESSION";
  private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(5);
  private final SecureRandom random = new SecureRandom();
  private final JdbcTemplate db;
  private final AccountRepository accounts;
  private final Duration ttl;

  PersistentSessionStore(
      JdbcTemplate db,
      AccountRepository accounts,
      @Value("${yfc.session.ttl-hours:12}") long ttlHours) {
    this.db = db;
    this.accounts = accounts;
    this.ttl = Duration.ofHours(Math.max(1, ttlHours));
  }

  @Transactional
  String issue(AccountPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    String hashed = AccountService.hash(token);
    Instant now = Instant.now();
    Instant expires = now.plus(ttl);
    String role =
        principal.getAuthorities().stream()
            .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
            .findFirst()
            .orElse("CUSTOMER");
    db.update(
        "insert into persistent_sessions(session_id,account_id,username,role,auth_version,"
            + "created_at,last_accessed_at,expires_at) values(?,?,?,?,?,?,?,?)",
        hashed,
        principal.accountId,
        principal.getUsername(),
        role,
        principal.authVersion,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(expires));
    // Opportunistic purge of expired sessions so the table stays bounded.
    db.update("delete from persistent_sessions where expires_at < ?", Timestamp.from(now));
    writeCookie(response, token, ttl.toSeconds(), request.isSecure());
    return token;
  }

  @Transactional
  Optional<AccountPrincipal> resolve(HttpServletRequest request) {
    String token = readCookie(request);
    if (token == null || token.isBlank()) return Optional.empty();
    String hashed = AccountService.hash(token);
    Instant now = Instant.now();
    var rows =
        db.queryForList(
            "select account_id, auth_version, last_accessed_at from persistent_sessions"
                + " where session_id=? and expires_at > ?",
            hashed,
            Timestamp.from(now));
    if (rows.isEmpty()) return Optional.empty();
    var row = norm(rows.getFirst());
    UUID accountId = (UUID) row.get("account_id");
    long savedAuthVersion = ((Number) row.get("auth_version")).longValue();
    var optAccount = accounts.findById(accountId);
    if (optAccount.isEmpty()) {
      db.update("delete from persistent_sessions where session_id=?", hashed);
      return Optional.empty();
    }
    Account account = optAccount.get();
    if (!account.enabled || account.authVersion != savedAuthVersion) {
      db.update("delete from persistent_sessions where session_id=?", hashed);
      return Optional.empty();
    }
    Instant lastAccessed = toInstant(row.get("last_accessed_at"));
    if (lastAccessed == null || Duration.between(lastAccessed, now).compareTo(REFRESH_INTERVAL) > 0) {
      db.update(
          "update persistent_sessions set last_accessed_at=?, expires_at=? where session_id=?",
          Timestamp.from(now),
          Timestamp.from(now.plus(ttl)),
          hashed);
    }
    return Optional.of(new AccountPrincipal(account));
  }

  @Transactional
  void revoke(HttpServletRequest request, HttpServletResponse response) {
    String token = readCookie(request);
    if (token != null && !token.isBlank()) {
      db.update("delete from persistent_sessions where session_id=?", AccountService.hash(token));
    }
    writeCookie(response, "", 0, request.isSecure());
  }

  @Transactional
  void revokeAccount(UUID accountId) {
    db.update("delete from persistent_sessions where account_id=?", accountId);
  }

  private String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
    }
    return null;
  }

  private void writeCookie(
      HttpServletResponse response, String value, long maxAgeSeconds, boolean secure) {
    ResponseCookie cookie =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAgeSeconds)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private Map<String, Object> norm(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return result;
  }

  private Instant toInstant(Object v) {
    if (v instanceof Instant i) return i;
    if (v instanceof Timestamp t) return t.toInstant();
    if (v instanceof java.time.OffsetDateTime o) return o.toInstant();
    return null;
  }
}
