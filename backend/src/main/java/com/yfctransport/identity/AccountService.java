package com.yfctransport.identity;

import com.yfctransport.audit.AuditTrail;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class AccountService implements ExternalAccounts {
  private final AccountRepository accounts;
  private final PasswordResetRepository resets;
  private final IdentityGuardRepository guard;
  private final PasswordEncoder encoder;
  private final AuditTrail audit;

  AccountService(
      AccountRepository accounts,
      PasswordResetRepository resets,
      IdentityGuardRepository guard,
      PasswordEncoder encoder,
      AuditTrail audit) {
    this.accounts = accounts;
    this.resets = resets;
    this.guard = guard;
    this.encoder = encoder;
    this.audit = audit;
  }

  @Override
  public void lock() {
    guard.acquire();
  }

  @Override
  public boolean usernameTaken(String username) {
    return accounts.findByUsername(username.toLowerCase(Locale.ROOT)).isPresent();
  }

  @Override
  public void validatePassword(String password) {
    if (password == null
        || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Password must have at least 12 characters and at most 72 UTF-8 bytes");
  }

  @Override
  public String encode(String password) {
    validatePassword(password);
    return encoder.encode(password);
  }

  @Override
  public boolean passwordMatches(String raw, String hash) {
    return encoder.matches(raw, hash);
  }

  @Override
  public UUID openHashed(String username, String displayName, String passwordHash, String role) {
    guard.acquire();
    String name = username.toLowerCase(Locale.ROOT);
    if (!name.matches("[a-z0-9][a-z0-9._-]{2,79}"))
      throw new IllegalArgumentException("Invalid username");
    Role assigned = Role.valueOf(role);
    if (assigned != Role.CUSTOMER && assigned != Role.SUPPLIER && assigned != Role.DRIVER)
      throw new IllegalArgumentException("Only a customer, supplier or driver can be opened here");
    if (accounts.findByUsername(name).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
    Account a = accounts.saveAndFlush(new Account(name, displayName.strip(), passwordHash, assigned));
    audit.record("USER_CREATED", "USER", a.id, "Approved " + assigned + " application");
    return a.id;
  }

  @Override
  @Transactional(readOnly = true)
  public UUID idOf(String username) {
    return accounts
        .findByUsername(username.toLowerCase(Locale.ROOT))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
        .id;
  }

  Account create(String username, String name, String password, Role role) {
    guard.acquire();
    requireCurrentAdmin();
    validatePassword(password);
    Account a =
        accounts.save(
            new Account(
                username.toLowerCase(Locale.ROOT), name.strip(), encoder.encode(password), role));
    audit.record("USER_CREATED", "USER", a.id, "Assigned role " + role);
    return a;
  }

  Account update(UUID id, Role role, boolean enabled) {
    guard.acquire();
    requireCurrentAdmin();
    Account a = find(id);
    String actor = SecurityContextHolder.getContext().getAuthentication().getName();
    if (a.username.equals(actor) && (!enabled || role != Role.SUPER_ADMIN))
      throw new IllegalArgumentException(
          "You cannot disable or demote your own administrator account");
    if (a.role == Role.SUPER_ADMIN
        && a.enabled
        && (!enabled || role != Role.SUPER_ADMIN)
        && accounts.countByRoleAndEnabledTrue(Role.SUPER_ADMIN) <= 1)
      throw new IllegalArgumentException("At least one active administrator is required");
    String before = a.role + "/" + a.enabled;
    if (a.role != role || a.enabled != enabled) {
      a.role = role;
      a.enabled = enabled;
      a.authVersion++;
      for (PasswordReset reset : resets.findByAccountIdAndUsedFalse(a.id)) reset.used = true;
      audit.record("USER_ACCESS_CHANGED", "USER", a.id, before + " -> " + role + "/" + enabled);
    }
    return a;
  }

  private void requireCurrentAdmin() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    Account actor =
        accounts
            .findByUsername(auth.getName())
            .orElseThrow(() -> new AccessDeniedException("Administrator required"));
    if (!actor.enabled || actor.role != Role.SUPER_ADMIN)
      throw new AccessDeniedException("Administrator required");
  }

  record ResetIssued(String token, Instant expiresAt) {}

  ResetIssued issueReset(UUID id) {
    guard.acquire();
    requireCurrentAdmin();
    Account a = find(id);
    if (!a.enabled)
      throw new IllegalArgumentException("Enable the account before resetting its password");
    for (PasswordReset prior : resets.findByAccountIdAndUsedFalse(id)) prior.used = true;
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiry = Instant.now().plus(Duration.ofMinutes(15));
    resets.save(new PasswordReset(id, hash(token), expiry));
    audit.record("PASSWORD_RESET_ISSUED", "USER", id, "Reset credential expires in 15 minutes");
    return new ResetIssued(token, expiry);
  }

  void reset(String token, String password) {
    validatePassword(password);
    guard.acquire();
    PasswordReset reset =
        resets
            .findByTokenHash(hash(token))
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));
    if (reset.used || !reset.expiresAt.isAfter(Instant.now()))
      throw new IllegalArgumentException("Invalid or expired reset token");
    Account a = find(reset.accountId);
    if (!a.enabled) throw new IllegalArgumentException("Invalid or expired reset token");
    a.passwordHash = encoder.encode(password);
    a.authVersion++;
    for (PasswordReset prior : resets.findByAccountIdAndUsedFalse(a.id)) prior.used = true;
    audit.record(
        "PASSWORD_RESET_COMPLETED", "USER", a.id, "Password changed; previous sessions revoked");
  }

  @Transactional(readOnly = true)
  Account find(UUID id) {
    return accounts
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  void bootstrap(String username, String password) {
    guard.acquire();
    if (accounts.count() != 0) return;
    if (!username.matches("[a-z0-9][a-z0-9._-]{2,79}"))
      throw new IllegalArgumentException("Invalid bootstrap username");
    validatePassword(password);
    Account a =
        accounts.save(
            new Account(username, "Administrator", encoder.encode(password), Role.SUPER_ADMIN));
    audit.record("ADMIN_BOOTSTRAPPED", "USER", a.id, "Initial administrator created");
  }
}
