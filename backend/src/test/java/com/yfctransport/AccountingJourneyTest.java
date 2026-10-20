package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.*;
import java.math.BigDecimal;
import java.time.*;
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
@WithMockUser(username = "ledger-submitter", roles = "SUPER_ADMIN")
class AccountingJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  SalesJourneyTest h;
  String branch, cash, equity, period;
  LocalDate start, date, end;

  String uid() {
    return UUID.randomUUID().toString();
  }

  String code() {
    return "A" + uid().replace("-", "").substring(0, 15);
  }

  @BeforeEach
  void setup() throws Exception {
    h = new SalesJourneyTest();
    h.mvc = mvc;
    h.json = json;
    h.db = db;
    branch =
        h.id(
            h.create(
                "/api/v1/branches",
                Map.of(
                    "code",
                    code(),
                    "name",
                    "Accounting test branch",
                    "address",
                    "Office",
                    "city",
                    "Gurgaon",
                    "state",
                    "Haryana",
                    "pincode",
                    "122001")));
    cash = account("ASSET");
    equity = account("EQUITY");
    var min =
        db.queryForObject("select min(starts_on) from financial_periods", java.sql.Date.class);
    start = min == null ? LocalDate.of(2010, 1, 1) : min.toLocalDate().minusDays(20);
    date = start.plusDays(5);
    end = start.plusDays(9);
    var r = periodBody(start, end);
    period = (String) r.get("periodId");
    h.create("/api/v1/accounting/periods", r);
  }

  String account(String type) throws Exception {
    return h.id(
        h.create(
            "/api/v1/accounting/accounts",
            Map.of("accountId", uid(), "code", code(), "name", "Test " + type, "type", type)));
  }

  Map<String, Object> periodBody(LocalDate from, LocalDate to) {
    return Map.of(
        "periodId", uid(), "code", code(), "startsOn", from.toString(), "endsOn", to.toString());
  }

  Map<String, Object> line(String account, String debit, String credit) {
    return Map.of(
        "accountId",
        account,
        "debit",
        new BigDecimal(debit),
        "credit",
        new BigDecimal(credit),
        "narration",
        "Verified supporting record");
  }

  Map<String, Object> journalBody(String amount) {
    return new HashMap<>(
        Map.of(
            "journalId",
            uid(),
            "periodId",
            period,
            "branchId",
            branch,
            "entryDate",
            date.toString(),
            "description",
            "Accountant-approved manual transaction",
            "reference",
            uid(),
            "lines",
            List.of(line(cash, amount, "0"), line(equity, "0", amount))));
  }

  String submit(Map<String, Object> body) throws Exception {
    h.create("/api/v1/accounting/journals", body);
    return body.get("journalId").toString();
  }

  Map<String, Object> decision() {
    return Map.of(
        "eventId", uid(), "reason", "Supporting records and balanced entry independently checked");
  }

  int actAs(String path, Object body, String username, String role) throws Exception {
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

  int review(String id, String action, Map<String, Object> body) throws Exception {
    return actAs(
        "/api/v1/accounting/journals/" + id + "/" + action, body, "ledger-reviewer", "ACCOUNTS");
  }

  void postJournal(String id) throws Exception {
    assertThat(review(id, "post", decision())).isEqualTo(200);
  }

  DocumentContext getJson(String path) throws Exception {
    return JsonPath.parse(
        mvc.perform(get(path))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void balancedJournalPostsOnlyAfterIndependentReviewAndRetriesExactly() throws Exception {
    var r = journalBody("100.25");
    String id = submit(r);
    assertThat(
            getJson("/api/v1/accounting/trial-balance?asOf=" + end + "&branchId=" + branch)
                .read("$.accounts", List.class))
        .isEmpty();
    assertThat(
            actAs(
                "/api/v1/accounting/journals/" + id + "/post",
                decision(),
                "ledger-submitter",
                "SUPER_ADMIN"))
        .isEqualTo(409);
    var event = decision();
    assertThat(review(id, "post", event)).isEqualTo(200);
    assertThat(review(id, "post", event)).isEqualTo(200);
    action("/api/v1/accounting/journals", r, 200);
    var journal = getJson("/api/v1/accounting/journals/" + id);
    assertThat(journal.read("$.number", String.class)).startsWith("JV-");
    assertThat(journal.read("$.status", String.class)).isEqualTo("POSTED");
    var trial = getJson("/api/v1/accounting/trial-balance?asOf=" + end + "&branchId=" + branch);
    assertThat(trial.read("$.debitBalance", Double.class)).isEqualTo(100.25);
    assertThat(trial.read("$.balanced", Boolean.class)).isTrue();
    assertThat(
            db.queryForObject(
                "select count(*) from audit_events where target_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(2);
  }

  @Test
  void invalidLinesBalancesAndPrecisionRejected() throws Exception {
    var r = journalBody("10");
    r.put("lines", List.of(line(cash, "10", "0"), line(equity, "0", "9")));
    action("/api/v1/accounting/journals", r, 400);
    r = journalBody("10");
    r.put("lines", List.of(line(cash, "10", "10"), line(equity, "0", "10")));
    action("/api/v1/accounting/journals", r, 400);
    r = journalBody("10");
    r.put("lines", List.of(line(cash, "0", "0"), line(equity, "0", "10")));
    action("/api/v1/accounting/journals", r, 400);
    action("/api/v1/accounting/journals", journalBody("0.001"), 400);
    r = journalBody("10");
    r.put("lines", List.of(line(cash, "10", "0")));
    action("/api/v1/accounting/journals", r, 400);
  }

  @Test
  void conflictingCreationAndReviewEventsCannotRewritePostedData() throws Exception {
    var r = journalBody("10");
    String id = submit(r);
    r.put("description", "Different");
    action("/api/v1/accounting/journals", r, 409);
    var d = decision();
    assertThat(review(id, "post", d)).isEqualTo(200);
    assertThat(review(id, "reject", d)).isEqualTo(409);
    assertThat(review(id, "reject", decision())).isEqualTo(409);
    String other = submit(journalBody("5"));
    assertThat(review(other, "post", d)).isEqualTo(409);
  }

  @Test
  void periodsCannotOverlapAndInvalidDatesAreRejected() throws Exception {
    action("/api/v1/accounting/periods", periodBody(date, end.plusDays(1)), 409);
    action("/api/v1/accounting/periods", periodBody(end.plusDays(2), end.plusDays(1)), 400);
    var r = journalBody("10");
    r.put("entryDate", end.plusDays(1).toString());
    action("/api/v1/accounting/journals", r, 400);
    var future =
        periodBody(
            LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(20),
            LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(20).plusDays(2));
    // Allocate a future period outside any prior test-created period, so reruns remain isolated.
    var max =
        db.queryForObject("select max(ends_on) from financial_periods", java.sql.Date.class)
            .toLocalDate();
    LocalDate futureStart =
        max.isAfter(LocalDate.now()) ? max.plusDays(5) : LocalDate.now().plusYears(20);
    future = periodBody(futureStart, futureStart.plusDays(2));
    h.create("/api/v1/accounting/periods", future);
    r = journalBody("10");
    r.put("periodId", future.get("periodId"));
    r.put("entryDate", futureStart.toString());
    action("/api/v1/accounting/journals", r, 400);
  }

  @Test
  void closeRequiresNoDraftsAndBlocksNewEntries() throws Exception {
    String id = submit(journalBody("10"));
    var d = decision();
    String path = "/api/v1/accounting/periods/" + period + "/close";
    action(path, d, 409);
    assertThat(review(id, "reject", decision())).isEqualTo(200);
    action(path, d, 200);
    action(path, d, 200);
    action("/api/v1/accounting/journals", journalBody("10"), 409);
    assertThat(review(id, "post", decision())).isEqualTo(409);
  }

  @Test
  void reversalCreatesContraEntryAndCannotBeDuplicated() throws Exception {
    String original = submit(journalBody("20"));
    postJournal(original);
    var r = reversal();
    String path = "/api/v1/accounting/journals/" + original + "/reversals";
    h.create(path, r);
    action(path, r, 200);
    String reverse = (String) r.get("journalId");
    action(path, reversal(), 409);
    postJournal(reverse);
    action(path, reversal(), 409);
    assertThat(getJson("/api/v1/accounting/journals/" + original).read("$.status", String.class))
        .isEqualTo("POSTED");
    assertThat(
            getJson("/api/v1/accounting/trial-balance?asOf=" + end + "&branchId=" + branch)
                .read("$.debitBalance", Integer.class))
        .isZero();
    action("/api/v1/accounting/journals/" + reverse + "/reversals", reversal(), 409);
  }

  Map<String, Object> reversal() {
    return new HashMap<>(
        Map.of(
            "journalId",
            uid(),
            "periodId",
            period,
            "entryDate",
            date.plusDays(1).toString(),
            "reason",
            "Correction approved from source records",
            "reference",
            uid()));
  }

  @Test
  void rejectedReversalReleasesClaimAndClosedSourceCanReverseInOpenPeriod() throws Exception {
    String original = submit(journalBody("15"));
    postJournal(original);
    var first = reversal();
    String path = "/api/v1/accounting/journals/" + original + "/reversals";
    h.create(path, first);
    assertThat(review((String) first.get("journalId"), "reject", decision())).isEqualTo(200);
    action("/api/v1/accounting/periods/" + period + "/close", decision(), 200);
    LocalDate nextStart = end.plusDays(1);
    var next = periodBody(nextStart, nextStart.plusDays(2));
    h.create("/api/v1/accounting/periods", next);
    var r = reversal();
    r.put("periodId", next.get("periodId"));
    r.put("entryDate", nextStart.toString());
    h.create(path, r);
    postJournal((String) r.get("journalId"));
  }

  @Test
  void inactiveAccountBlocksPostingButRetainsHistoricalLedger() throws Exception {
    String posted = submit(journalBody("10"));
    postJournal(posted);
    String pending = submit(journalBody("5"));
    mvc.perform(
            patch("/api/v1/accounting/accounts/" + cash + "/status")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "expectedActive", true, "active", false, "reason", "Account retired"))))
        .andExpect(status().isOk());
    assertThat(review(pending, "post", decision())).isEqualTo(409);
    assertThat(review(pending, "reject", decision())).isEqualTo(200);
    var ledger =
        getJson(
            "/api/v1/accounting/accounts/"
                + cash
                + "/ledger?from="
                + start
                + "&to="
                + end
                + "&branchId="
                + branch);
    assertThat(ledger.read("$.closingBalance", Integer.class)).isEqualTo(10);
    assertThat(getJson("/api/v1/accounting/accounts/" + cash).read("$.status_events", List.class))
        .hasSize(1);
  }

  @Test
  void ledgerOpeningRunningAndPaginationAreConsistent() throws Exception {
    postJournal(submit(journalBody("10")));
    var r = journalBody("5.50");
    r.put("entryDate", date.plusDays(1).toString());
    postJournal(submit(r));
    String path =
        "/api/v1/accounting/accounts/"
            + cash
            + "/ledger?from="
            + date.plusDays(1)
            + "&to="
            + end
            + "&branchId="
            + branch;
    var ledger = getJson(path);
    assertThat(ledger.read("$.openingBalance", Integer.class)).isEqualTo(10);
    assertThat(ledger.read("$.closingBalance", Double.class)).isEqualTo(15.5);
    assertThat(ledger.read("$.entries[0].running_balance", Double.class)).isEqualTo(15.5);
    ledger =
        getJson(
            "/api/v1/accounting/accounts/"
                + cash
                + "/ledger?from="
                + start
                + "&to="
                + end
                + "&branchId="
                + branch
                + "&page=1&size=1");
    assertThat(ledger.read("$.entries[0].running_balance", Double.class)).isEqualTo(15.5);
  }

  @Test
  void permissionsAndCsrfProtectAccounting() throws Exception {
    for (String role : List.of("OPERATIONS", "CUSTOMER", "SUPPLIER", "DRIVER"))
      mvc.perform(get("/api/v1/accounting/journals").with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    assertThat(
            actAs(
                "/api/v1/accounting/periods",
                periodBody(start.minusDays(5), start.minusDays(1)),
                "accounts",
                "ACCOUNTS"))
        .isEqualTo(403);
    mvc.perform(
            post("/api/v1/accounting/journals")
                .contentType("application/json")
                .content(json.writeValueAsString(journalBody("10"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/accounting/accounts").with(user("accounts").roles("ACCOUNTS")))
        .andExpect(status().isOk());
  }

  @Test
  void concurrentReviewHasOneWinnerAndOneNumber() throws Exception {
    String id = submit(journalBody("10"));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Integer> task =
          () -> {
            gate.await();
            return review(id, "post", decision());
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
            db.queryForObject(
                "select count(*) from accounting_events where target_id=? and action='POST'",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void concurrentCloseAndDraftCreationCannotLeaveDraftInClosedPeriod() throws Exception {
    var gate = new CountDownLatch(1);
    var r = journalBody("10");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return actAs(
                    "/api/v1/accounting/periods/" + period + "/close",
                    decision(),
                    "closing-admin",
                    "SUPER_ADMIN");
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return actAs("/api/v1/accounting/journals", r, "accounts", "ACCOUNTS");
              });
      gate.countDown();
      int close = a.get(30, TimeUnit.SECONDS), draft = b.get(30, TimeUnit.SECONDS);
      assertThat((close == 200 && draft == 409) || (close == 409 && draft == 201)).isTrue();
    }
    assertThat(
            db.queryForObject(
                "select count(*) from journals j join financial_periods p on p.id=j.period_id where"
                    + " p.id=? and p.status='CLOSED' and j.status='DRAFT'",
                Integer.class,
                UUID.fromString(period)))
        .isZero();
  }

  DocumentContext action(String path, Object body, int expected) throws Exception {
    String text =
        mvc.perform(
                post(path)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.parse(text.isBlank() ? "{}" : text);
  }

  @Test
  void chartAndPeriodCreationRetryWithoutDuplicates() throws Exception {
    var body = Map.of("accountId", uid(), "code", code(), "name", "Bank account", "type", "ASSET");
    h.create("/api/v1/accounting/accounts", body);
    action("/api/v1/accounting/accounts", body, 200);
    var changed = new HashMap<String, Object>(body);
    changed.put("name", "Changed");
    action("/api/v1/accounting/accounts", changed, 409);
    changed = new HashMap<>(body);
    changed.put("accountId", uid());
    action("/api/v1/accounting/accounts", changed, 409);
    var p = periodBody(start.minusDays(5), start.minusDays(1));
    h.create("/api/v1/accounting/periods", p);
    action("/api/v1/accounting/periods", p, 200);
  }

  @Test
  void rejectedAndLaterJournalsAreExcludedAndLargeValuesStayExact() throws Exception {
    String rejected = submit(journalBody("10"));
    assertThat(review(rejected, "reject", decision())).isEqualTo(200);
    String id = submit(journalBody("999999999999.99"));
    postJournal(id);
    assertThat(
            db.queryForObject(
                "select total_debit from journals where id=?",
                BigDecimal.class,
                UUID.fromString(id)))
        .isEqualByComparingTo("999999999999.99");
    assertThat(
            getJson(
                    "/api/v1/accounting/trial-balance?asOf="
                        + date.minusDays(1)
                        + "&branchId="
                        + branch)
                .read("$.accounts", List.class))
        .isEmpty();
    assertThat(
            getJson(
                    "/api/v1/accounting/accounts/"
                        + cash
                        + "/ledger?from="
                        + start
                        + "&to="
                        + end
                        + "&branchId="
                        + branch)
                .read("$.entries", List.class))
        .hasSize(1);
  }

  @Test
  void draftReversalAndPredatingReversalAreRejected() throws Exception {
    String id = submit(journalBody("10"));
    String path = "/api/v1/accounting/journals/" + id + "/reversals";
    action(path, reversal(), 409);
    postJournal(id);
    var r = reversal();
    r.put("entryDate", date.minusDays(1).toString());
    action(path, r, 400);
    var current =
        periodBody(
            LocalDate.now(ZoneId.of("Asia/Kolkata")), LocalDate.now(ZoneId.of("Asia/Kolkata")));
    var existingToday =
        db.query(
            "select id from financial_periods where starts_on<=? and ends_on>=?",
            (rs, n) -> rs.getObject(1, UUID.class),
            LocalDate.now(ZoneId.of("Asia/Kolkata")),
            LocalDate.now(ZoneId.of("Asia/Kolkata")));
    if (existingToday.isEmpty()) h.create("/api/v1/accounting/periods", current);
    else {
      current = new HashMap<>(current);
      current.put("periodId", existingToday.getFirst().toString());
    }
    action("/api/v1/accounting/periods/" + current.get("periodId") + "/close", decision(), 409);
  }

  @Test
  void concurrentReversalsReserveOnlyOneContraDraft() throws Exception {
    String id = submit(journalBody("10"));
    postJournal(id);
    var gate = new CountDownLatch(1);
    String path = "/api/v1/accounting/journals/" + id + "/reversals";
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Integer> task =
          () -> {
            gate.await();
            return actAs(path, reversal(), "accounts", "ACCOUNTS");
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  @Test
  void concurrentDifferentJournalsReceiveDifferentNumbers() throws Exception {
    String first = submit(journalBody("1")), second = submit(journalBody("2"));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return review(first, "post", decision());
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return review(second, "post", decision());
              });
      gate.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactly(200, 200);
    }
    assertThat(
            db.queryForObject(
                "select number from journals where id=?", String.class, UUID.fromString(first)))
        .isNotEqualTo(
            db.queryForObject(
                "select number from journals where id=?", String.class, UUID.fromString(second)));
  }

  @Test
  void failedAuditRollsBackPostingNumberAndEvent() throws Exception {
    String id = submit(journalBody("10"));
    long before =
        db.queryForObject("select next_number from accounting_guard where id=1", Long.class);
    // Deliberately exceed the audit actor limit after the posting writes, to force rollback.
    assertThat(
            actAs(
                "/api/v1/accounting/journals/" + id + "/post",
                decision(),
                "a".repeat(81),
                "ACCOUNTS"))
        .isEqualTo(409);
    assertThat(
            db.queryForObject(
                "select status from journals where id=?", String.class, UUID.fromString(id)))
        .isEqualTo("DRAFT");
    assertThat(
            db.queryForObject(
                "select count(*) from accounting_events where target_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isZero();
    assertThat(db.queryForObject("select next_number from accounting_guard where id=1", Long.class))
        .isEqualTo(before);
    postJournal(id);
  }
}
