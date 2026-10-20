package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "YFC_ADMIN_PASSWORD=local-test-password-only")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class IdentityJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  static final String PASSWORD = "initial-test-password";

  MockHttpSession login(String username, String password) throws Exception {
    return (MockHttpSession)
        mvc.perform(formLogin().user(username).password(password))
            .andExpect(status().isNoContent())
            .andReturn()
            .getRequest()
            .getSession(false);
  }

  MockHttpSession admin() throws Exception {
    return login("admin", "local-test-password-only");
  }

  String username() {
    return "u-" + UUID.randomUUID();
  }

  String create(MockHttpSession admin, String username, String role) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/users")
                    .session(admin)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        """
                        {"username":"%s","displayName":"Test User","password":"%s","role":"%s"}
                        """
                            .formatted(username, PASSWORD, role)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  void access(MockHttpSession admin, String id, String role, boolean enabled) throws Exception {
    mvc.perform(
            patch("/api/v1/users/" + id + "/access")
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content("{\"role\":\"" + role + "\",\"enabled\":" + enabled + "}"))
        .andExpect(status().isOk());
  }

  String resetToken(MockHttpSession admin, String id) throws Exception {
    return JsonPath.read(
        mvc.perform(post("/api/v1/users/" + id + "/password-reset").session(admin).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.token");
  }

  void completeReset(String token, String password, int expectedStatus) throws Exception {
    mvc.perform(
            post("/api/v1/auth/password-reset")
                .with(csrf())
                .contentType("application/json")
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"))
        .andExpect(status().is(expectedStatus));
  }

  @Test
  void operationsAndAccountsHaveDifferentPermissionsAndWritesAreAudited() throws Exception {
    var admin = admin();
    String ops = username(), accounts = username();
    create(admin, ops, "OPERATIONS");
    create(admin, accounts, "ACCOUNTS");
    var opsSession = login(ops, PASSWORD);
    var accountsSession = login(accounts, PASSWORD);
    String customer =
        JsonPath.read(
            mvc.perform(
                    post("/api/v1/customers")
                        .session(opsSession)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Site buyer\",\"mobile\":\"9876543210\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    assertThat(
            jdbc.queryForObject(
                "select actor from audit_events where target_id=? and action='CUSTOMER_CREATED'",
                String.class,
                UUID.fromString(customer)))
        .isEqualTo(ops);
    mvc.perform(get("/api/v1/customers").session(accountsSession)).andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/customers")
                .session(accountsSession)
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"No access\",\"mobile\":\"9876543210\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/users").session(opsSession)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/audit-events").session(accountsSession))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/users").session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
  }

  @Test
  void disablingAnAccountRevokesExistingSessionsAndPreventsLogin() throws Exception {
    var admin = admin();
    String name = username();
    String id = create(admin, name, "OPERATIONS");
    var session = login(name, PASSWORD);
    access(admin, id, "OPERATIONS", false);
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    mvc.perform(formLogin().user(name).password(PASSWORD)).andExpect(status().isUnauthorized());
    access(admin, id, "OPERATIONS", true);
    mvc.perform(get("/api/v1/auth/me").session(login(name, PASSWORD))).andExpect(status().isOk());
  }

  @Test
  void roleChangesRevokeOldSessionsAndApplyNewPermissions() throws Exception {
    var admin = admin();
    String name = username();
    String id = create(admin, name, "OPERATIONS");
    var session = login(name, PASSWORD);
    access(admin, id, "ACCOUNTS", true);
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    var fresh = login(name, PASSWORD);
    mvc.perform(get("/api/v1/auth/me").session(fresh))
        .andExpect(jsonPath("$.roles[0]").value("ROLE_ACCOUNTS"));
    mvc.perform(
            post("/api/v1/trucks")
                .session(fresh)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"registration\":\"HR55AB1001\",\"capacityTonnes\":35,\"tankCapacityLitres\":400,\"mileageKmPerLitre\":3}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void resetIsHashedSingleUseAndRevokesSessionsWithoutLeakingSecretsToAudit() throws Exception {
    var admin = admin();
    String name = username();
    String id = create(admin, name, "OPERATIONS");
    var session = login(name, PASSWORD);
    String token = resetToken(admin, id);
    String stored =
        jdbc.queryForObject(
            "select token_hash from password_resets where account_id=? and used=false",
            String.class,
            UUID.fromString(id));
    assertThat(stored).hasSize(64).isNotEqualTo(token);
    String replacement = "replacement-test-password";
    completeReset(token, replacement, 204);
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    mvc.perform(formLogin().user(name).password(PASSWORD)).andExpect(status().isUnauthorized());
    login(name, replacement);
    completeReset(token, "another-test-password", 400);
    String audit =
        mvc.perform(get("/api/v1/audit-events?size=100").session(admin))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(audit).doesNotContain(token, replacement, PASSWORD, "passwordHash");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where target_id=? and"
                    + " action='PASSWORD_RESET_COMPLETED'",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void replacementAndExpiredResetTokensAreRejected() throws Exception {
    var admin = admin();
    String id = create(admin, username(), "ACCOUNTS");
    String old = resetToken(admin, id), current = resetToken(admin, id);
    completeReset(old, "replacement-test-password", 400);
    jdbc.update(
        "update password_resets set expires_at=? where account_id=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),
        UUID.fromString(id));
    completeReset(current, "replacement-test-password", 400);
  }

  @Test
  void administratorCannotDisableOrDemoteTheirOwnAccount() throws Exception {
    var admin = admin();
    String id =
        jdbc.queryForObject("select id from user_accounts where username='admin'", UUID.class)
            .toString();
    mvc.perform(
            patch("/api/v1/users/" + id + "/access")
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content("{\"role\":\"ACCOUNTS\",\"enabled\":true}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/v1/users/" + id + "/access")
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content("{\"role\":\"SUPER_ADMIN\",\"enabled\":false}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void externalRolesCannotReadInternalDataOrManageUsers() throws Exception {
    var admin = admin();
    for (String role : java.util.List.of("CUSTOMER", "SUPPLIER", "DRIVER")) {
      String name = username();
      create(admin, name, role);
      var session = login(name, PASSWORD);
      mvc.perform(get("/api/v1/customers").session(session)).andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/users").session(session)).andExpect(status().isForbidden());
      mvc.perform(
              post("/api/v1/users/00000000-0000-0000-0000-000000000001/password-reset")
                  .session(session)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void duplicateNamesAndWeakPasswordsAreRejected() throws Exception {
    var admin = admin();
    String name = username();
    create(admin, name, "ACCOUNTS");
    Integer eventsBefore =
        jdbc.queryForObject(
            "select count(*) from audit_events where action='USER_CREATED'", Integer.class);
    mvc.perform(
            post("/api/v1/users")
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"username\":\""
                        + name.toUpperCase(java.util.Locale.ROOT)
                        + "\",\"displayName\":\"Duplicate\",\"password\":\""
                        + PASSWORD
                        + "\",\"role\":\"ACCOUNTS\"}"))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where action='USER_CREATED'", Integer.class))
        .isEqualTo(eventsBefore);

    mvc.perform(
            post("/api/v1/users")
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"username\":\"weak-password-test\",\"displayName\":\"Weak\",\"password\":\"short\",\"role\":\"ACCOUNTS\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void resetTokenCannotBeUsedTwiceConcurrently() throws Exception {
    var admin = admin();
    String id = create(admin, username(), "ACCOUNTS");
    String token = resetToken(admin, id);
    java.util.concurrent.Callable<Integer> attempt =
        () ->
            mvc.perform(
                    post("/api/v1/auth/password-reset")
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            "{\"token\":\""
                                + token
                                + "\",\"newPassword\":\"concurrent-reset-password\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var attempts = executor.invokeAll(java.util.List.of(attempt, attempt));
      assertThat(java.util.List.of(attempts.get(0).get(), attempts.get(1).get()))
          .containsExactlyInAnyOrder(204, 400);
    }
  }

  @Test
  void disablingAnAccountAlsoInvalidatesOutstandingResetTokens() throws Exception {
    var admin = admin();
    String id = create(admin, username(), "ACCOUNTS");
    String token = resetToken(admin, id);
    access(admin, id, "ACCOUNTS", false);
    access(admin, id, "ACCOUNTS", true);
    completeReset(token, "replacement-test-password", 400);
  }

  @Test
  void passwordResetStillRequiresCsrfProtection() throws Exception {
    mvc.perform(
            post("/api/v1/auth/password-reset")
                .contentType("application/json")
                .content("{\"token\":\"invalid\",\"newPassword\":\"replacement-test-password\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void repeatedFailedSignInsAreLockedOut() throws Exception {
    var admin = admin();
    String user = username();
    create(admin, user, "OPERATIONS");
    for (int i = 0; i < 5; i++)
      mvc.perform(formLogin().user(user).password("wrong-password-xx"))
          .andExpect(status().isUnauthorized());
    var locked =
        mvc.perform(formLogin().user(user).password("wrong-password-xx"))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(locked.getResponse().getErrorMessage())
        .contains("Too many failed sign-in attempts");
    mvc.perform(formLogin().user(user).password(PASSWORD)).andExpect(status().isUnauthorized());
  }

  @Test
  void csrfTokenEnablesRealLoginAndLogoutEndsSession() throws Exception {
    var result = mvc.perform(get("/api/v1/csrf")).andExpect(status().isOk()).andReturn();
    String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    String parameter = JsonPath.read(result.getResponse().getContentAsString(), "$.parameterName");
    var session = (MockHttpSession) result.getRequest().getSession(false);
    String previousSessionId = session.getId();
    mvc.perform(
            post("/login")
                .session(session)
                .param("username", "admin")
                .param("password", "local-test-password-only")
                .param(parameter, token))
        .andExpect(status().isNoContent());
    assertThat(session.getId()).isNotEqualTo(previousSessionId);
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
    mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select actor from audit_events where action='LOGOUT' order by occurred_at desc"
                    + " limit 1",
                String.class))
        .isEqualTo("admin");
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }
}
