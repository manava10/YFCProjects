package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "incident-ops", roles = "OPERATIONS")
class IncidentJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  TripJourneyTest h;
  String trip;

  @BeforeEach
  void setup() throws Exception {
    h = new TripJourneyTest();
    h.mvc = mvc;
    h.json = json;
    h.db = db;
    h.setup();
    // Master setup is administrator-only; use its existing helper with a scoped security context.
    var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
    var original = context.getAuthentication();
    context.setAuthentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "incident-admin",
            "",
            List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_SUPER_ADMIN"))));
    try {
      trip = h.allocate(h.fixture("TRANSPORT"));
    } finally {
      context.setAuthentication(original);
    }
  }

  String base() {
    return "/api/v1/trips/" + trip + "/incidents";
  }

  Map<String, Object> report() {
    return new HashMap<>(
        Map.of(
            "incidentId",
            UUID.randomUUID().toString(),
            "category",
            "DELAY",
            "occurredAt",
            Instant.now().toString(),
            "description",
            "Road closure delayed departure",
            "evidenceReference",
            "Dispatch log 42"));
  }

  Map<String, Object> resolution() {
    return Map.of(
        "eventId",
        UUID.randomUUID().toString(),
        "resolution",
        "Road reopened and dispatcher informed",
        "evidenceReference",
        "Dispatch log 43");
  }

  int postAs(String path, Object body, String username, String role) throws Exception {
    return mvc.perform(
            post(path)
                .with(user(username).roles(role))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void reportResolveRetryAndPreserveTrip() throws Exception {
    var r = report();
    String path = base() + "/" + r.get("incidentId");
    h.action(base(), r, 201);
    h.action(base(), r, 200);
    var decision = resolution();
    h.action(path + "/resolve", decision, 200);
    h.action(path + "/resolve", decision, 200);
    var replay = h.action(base(), r, 200);
    assertThat(replay.read("$.status", String.class)).isEqualTo("RESOLVED");
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportedBy").value("incident-ops"))
        .andExpect(jsonPath("$.resolvedBy").value("incident-ops"))
        .andExpect(jsonPath("$.createPayload").doesNotExist());
    assertThat(
            db.queryForObject(
                "select status from trips where id=?", String.class, UUID.fromString(trip)))
        .isEqualTo("ALLOCATED");
    assertThat(
            db.queryForObject(
                "select count(*) from audit_events where target_id=?",
                Integer.class,
                UUID.fromString(r.get("incidentId").toString())))
        .isEqualTo(2);
  }

  @Test
  void conflictingIdsAndImmutableResolution() throws Exception {
    var r = report();
    h.action(base(), r, 201);
    var changed = new HashMap<>(r);
    changed.put("description", "Different description");
    h.action(base(), changed, 409);
    String path = base() + "/" + r.get("incidentId") + "/resolve";
    var d = resolution();
    h.action(path, d, 200);
    h.action(path, resolution(), 409);
    var other = report();
    h.action(base(), other, 201);
    h.action(base() + "/" + other.get("incidentId") + "/resolve", d, 409);
  }

  @Test
  void timestampsEvidenceAndCategoriesValidated() throws Exception {
    for (String at : List.of(Instant.now().plusSeconds(3600).toString(), "2000-01-01T00:00:00Z")) {
      var r = report();
      r.put("occurredAt", at);
      h.action(base(), r, 400);
    }
    var r = report();
    r.put("evidenceReference", " ");
    h.action(base(), r, 400);
    r = report();
    r.put("category", "REFUND");
    h.action(base(), r, 400);
    r = report();
    h.action(base(), r, 201);
    var d = new HashMap<>(resolution());
    d.put("resolution", " ");
    h.action(base() + "/" + r.get("incidentId") + "/resolve", d, 400);
  }

  @Test
  void rolesCsrfAndParentScope() throws Exception {
    for (String role : List.of("CUSTOMER", "SUPPLIER", "DRIVER", "ACCOUNTS"))
      assertThat(postAs(base(), report(), "other", role)).isEqualTo(403);
    mvc.perform(
            post(base()).contentType("application/json").content(json.writeValueAsString(report())))
        .andExpect(status().isForbidden());
    var r = report();
    h.action(base(), r, 201);
    mvc.perform(get(base()).with(user("accounts").roles("ACCOUNTS"))).andExpect(status().isOk());
    mvc.perform(get(base()).with(user("driver").roles("DRIVER"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/trips/" + UUID.randomUUID() + "/incidents/" + r.get("incidentId")))
        .andExpect(status().isNotFound());
    mvc.perform(get(base()).param("size", "101")).andExpect(status().isBadRequest());
  }

  @Test
  void cancelledTripAllowsExistingResolutionButNoNewReport() throws Exception {
    var r = report();
    h.action(base(), r, 201);
    h.action(
        "/api/v1/trips/" + trip + "/cancel",
        Map.of("eventId", UUID.randomUUID().toString(), "reason", "Customer postponed"),
        200);
    h.action(base(), report(), 409);
    h.action(base(), r, 200);
    h.action(base() + "/" + r.get("incidentId") + "/resolve", resolution(), 200);
  }

  @Test
  void concurrentConflictingResolutionsHaveOneWinner() throws Exception {
    var r = report();
    h.action(base(), r, 201);
    String path = base() + "/" + r.get("incidentId") + "/resolve";
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Integer> task =
          () -> {
            gate.await();
            return postAs(path, resolution(), "resolver", "OPERATIONS");
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  void concurrentIdenticalReportsCreateOneRecord() throws Exception {
    var report = report();
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Integer> task =
          () -> {
            gate.await();
            return postAs(base(), report, "reporter", "OPERATIONS");
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 200);
    }
    assertThat(
            db.queryForObject(
                "select count(*) from trip_incidents where id=?",
                Integer.class,
                UUID.fromString(report.get("incidentId").toString())))
        .isEqualTo(1);
  }
}
