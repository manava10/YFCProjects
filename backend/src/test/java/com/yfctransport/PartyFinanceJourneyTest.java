package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.*;
import java.math.BigDecimal;
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
class PartyFinanceJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  AccountingJourneyTest h;
  String customer, supplier;

  @BeforeEach
  void setup() throws Exception {
    h = new AccountingJourneyTest();
    h.mvc = mvc;
    h.json = json;
    h.db = db;
    h.setup();
    customer =
        h.h.id(
            h.h.create(
                "/api/v1/customers", Map.of("name", "Finance buyer", "mobile", "9876543210")));
    String pan =
        uid()
            .replace("-", "")
            .substring(0, 5)
            .chars()
            .mapToObj(c -> Character.toString((char) ('A' + Character.digit(c, 16))))
            .collect(java.util.stream.Collectors.joining());
    String gst =
        "06"
            + pan
            + String.format("%04d", Math.floorMod(UUID.randomUUID().hashCode(), 10000))
            + "F1ZA";
    supplier =
        h.h.id(
            h.h.create(
                "/api/v1/suppliers",
                Map.of(
                    "code",
                    h.code(),
                    "name",
                    "Finance supplier",
                    "gstin",
                    gst,
                    "mobile",
                    "9876543210",
                    "address",
                    "Office")));
  }

  String uid() {
    return UUID.randomUUID().toString();
  }

  Map<String, Object> profile(String type, String party, String account) {
    var r =
        new HashMap<String, Object>(
            Map.of(
                "profileId",
                uid(),
                "partyType",
                type,
                "partyId",
                party,
                "accountId",
                account,
                "paymentTermsDays",
                30));
    if (type.equals("CUSTOMER")) r.put("creditLimit", new BigDecimal("10000.00"));
    return r;
  }

  String create(Map<String, Object> body) throws Exception {
    h.h.create("/api/v1/accounting/parties", body);
    return body.get("profileId").toString();
  }

  DocumentContext action(String path, Object body, int expected) throws Exception {
    return h.action(path, body, expected);
  }

  DocumentContext getJson(String path) throws Exception {
    return h.getJson(path);
  }

  Map<String, Object> terms(int version, int days, String limit) {
    var r =
        new HashMap<String, Object>(
            Map.of(
                "eventId",
                uid(),
                "version",
                version,
                "paymentTermsDays",
                days,
                "reason",
                "Commercial terms confirmed by administration"));
    if (limit != null) r.put("creditLimit", new BigDecimal(limit));
    return r;
  }

  @Test
  void customerSupplierProfilesAndLookupsAreImmutableAndRetrySafe() throws Exception {
    var c = profile("CUSTOMER", customer, h.cash);
    String cid = create(c);
    action("/api/v1/accounting/parties", c, 200);
    String liability = h.account("LIABILITY");
    String sid = create(profile("SUPPLIER", supplier, liability));
    assertThat(
            getJson("/api/v1/accounting/parties/by-customer/" + customer)
                .read("$.id", String.class))
        .isEqualTo(cid);
    assertThat(
            getJson("/api/v1/accounting/parties/by-supplier/" + supplier)
                .read("$.id", String.class))
        .isEqualTo(sid);
    c.put("paymentTermsDays", 31);
    action("/api/v1/accounting/parties", c, 409);
    action("/api/v1/accounting/parties", profile("CUSTOMER", customer, h.account("ASSET")), 409);
  }

  @Test
  void wrongTypesInactiveAndHistoricallyUsedAccountsAreRejected() throws Exception {
    action("/api/v1/accounting/parties", profile("CUSTOMER", customer, h.equity), 409);
    action("/api/v1/accounting/parties", profile("SUPPLIER", supplier, h.cash), 409);
    String used = h.submit(h.journalBody("10"));
    assertThat(h.review(used, "reject", h.decision())).isEqualTo(200);
    action("/api/v1/accounting/parties", profile("CUSTOMER", customer, h.cash), 409);
    String inactive = h.account("ASSET");
    mvc.perform(
            patch("/api/v1/accounting/accounts/" + inactive + "/status")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("expectedActive", true, "active", false, "reason", "Retired"))))
        .andExpect(status().isOk());
    action("/api/v1/accounting/parties", profile("CUSTOMER", customer, inactive), 409);
  }

  @Test
  void missingPartiesSupplierLimitsAndInvalidTermsRejected() throws Exception {
    action("/api/v1/accounting/parties", profile("CUSTOMER", uid(), h.cash), 404);
    var r = profile("SUPPLIER", supplier, h.account("LIABILITY"));
    r.put("creditLimit", 10);
    action("/api/v1/accounting/parties", r, 400);
    r = profile("CUSTOMER", customer, h.cash);
    r.put("paymentTermsDays", 366);
    action("/api/v1/accounting/parties", r, 400);
    r = profile("CUSTOMER", customer, h.cash);
    r.put("creditLimit", new BigDecimal("0.001"));
    action("/api/v1/accounting/parties", r, 400);
    mvc.perform(get("/api/v1/accounting/parties/by-customer/" + uid()))
        .andExpect(status().isNotFound());
  }

  @Test
  void termsVersionHistoryAndOriginalCreationRetryArePreserved() throws Exception {
    var input = profile("CUSTOMER", customer, h.cash);
    String id = create(input);
    var first = terms(1, 45, "15000");
    String path = "/api/v1/accounting/parties/" + id + "/terms";
    action(path, first, 200);
    action(path, first, 200);
    action(path, terms(1, 60, "20000"), 409);
    action(path, terms(2, 60, "20000"), 200);
    var replay = action("/api/v1/accounting/parties", input, 200);
    assertThat(replay.read("$.version", Integer.class)).isEqualTo(3);
    assertThat(getJson("/api/v1/accounting/parties/" + id).read("$.terms_history", List.class))
        .hasSize(2);
    assertThat(
            db.queryForObject(
                "select credit_limit from party_financial_profiles where id=?",
                BigDecimal.class,
                UUID.fromString(id)))
        .isEqualByComparingTo("20000");
    action(path, terms(3, 60, "20000"), 400);
  }

  @Test
  void customerLedgerBalancesIncludeOnlyPostedEntriesAndReversals() throws Exception {
    String id = create(profile("CUSTOMER", customer, h.cash));
    String journal = h.submit(h.journalBody("100"));
    h.submit(h.journalBody("25"));
    String path =
        "/api/v1/accounting/parties/"
            + id
            + "/ledger?from="
            + h.start
            + "&to="
            + h.end
            + "&branchId="
            + h.branch;
    assertThat(getJson(path).read("$.balance_kind", String.class)).isEqualTo("ZERO_BALANCE");
    h.postJournal(journal);
    assertThat(getJson(path).read("$.balance_kind", String.class)).isEqualTo("RECEIVABLE");
    assertThat(getJson(path).read("$.balance_amount", Integer.class)).isEqualTo(100);
    var reversal = h.reversal();
    h.h.create("/api/v1/accounting/journals/" + journal + "/reversals", reversal);
    h.postJournal((String) reversal.get("journalId"));
    assertThat(getJson(path).read("$.balance_kind", String.class)).isEqualTo("ZERO_BALANCE");
    String credit = postTo(h.cash, false, "10");
    assertThat(credit).isNotBlank();
    assertThat(getJson(path).read("$.balance_kind", String.class)).isEqualTo("CUSTOMER_CREDIT");
  }

  String postTo(String account, boolean debit, String amount) throws Exception {
    var r = h.journalBody(amount);
    r.put(
        "lines",
        debit
            ? List.of(h.line(account, amount, "0"), h.line(h.equity, "0", amount))
            : List.of(h.line(h.equity, amount, "0"), h.line(account, "0", amount)));
    String journal = h.submit(r);
    h.postJournal(journal);
    return journal;
  }

  @Test
  void supplierPayableAndAdvanceAreLedgerBalancesNotPaymentStates() throws Exception {
    String account = h.account("LIABILITY"), id = create(profile("SUPPLIER", supplier, account));
    postTo(account, false, "50");
    String path = "/api/v1/accounting/parties/" + id + "/ledger?from=" + h.start + "&to=" + h.end;
    assertThat(getJson(path).read("$.balance_kind", String.class)).isEqualTo("PAYABLE");
    postTo(account, true, "60");
    assertThat(getJson(path).read("$.balance_kind", String.class)).isEqualTo("SUPPLIER_ADVANCE");
    assertThat(getJson(path).read("$.balance_amount", Integer.class)).isEqualTo(10);
  }

  @Test
  void balanceSummaryFiltersBranchAndDateAndMatchesLedger() throws Exception {
    String id = create(profile("CUSTOMER", customer, h.cash));
    postTo(h.cash, true, "123.45");
    int lastPage =
        (db.queryForObject("select count(*) from party_financial_profiles", Integer.class) - 1)
            / 100;
    String path =
        "/api/v1/accounting/party-balances?asOf="
            + h.end
            + "&branchId="
            + h.branch
            + "&size=100&page="
            + lastPage;
    var rows = getJson(path).read("$", List.class);
    var row =
        (Map<?, ?>)
            rows.stream()
                .filter(r -> id.equals(((Map<?, ?>) r).get("id")))
                .findFirst()
                .orElseThrow();
    assertThat(((Number) row.get("balance_amount")).doubleValue()).isEqualTo(123.45);
    rows =
        getJson(
                "/api/v1/accounting/party-balances?asOf="
                    + h.date.minusDays(1)
                    + "&branchId="
                    + h.branch
                    + "&size=100&page="
                    + lastPage)
            .read("$", List.class);
    row =
        (Map<?, ?>)
            rows.stream()
                .filter(r -> id.equals(((Map<?, ?>) r).get("id")))
                .findFirst()
                .orElseThrow();
    assertThat(((Number) row.get("balance_amount")).doubleValue()).isZero();
  }

  @Test
  void permissionsCsrfAndPageLimitsProtectPartyFinance() throws Exception {
    String id = create(profile("CUSTOMER", customer, h.cash));
    for (String role : List.of("OPERATIONS", "CUSTOMER", "SUPPLIER", "DRIVER"))
      mvc.perform(get("/api/v1/accounting/parties/" + id).with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    assertThat(
            h.actAs(
                "/api/v1/accounting/parties",
                profile("CUSTOMER", uid(), h.cash),
                "accounts",
                "ACCOUNTS"))
        .isEqualTo(403);
    assertThat(
            h.actAs(
                "/api/v1/accounting/parties/" + id + "/terms",
                terms(1, 45, "20000"),
                "accounts",
                "ACCOUNTS"))
        .isEqualTo(403);
    mvc.perform(
            post("/api/v1/accounting/parties")
                .contentType("application/json")
                .content(json.writeValueAsString(profile("CUSTOMER", customer, h.cash))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/accounting/parties").param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentTermUpdatesHaveOneWinner() throws Exception {
    String id = create(profile("CUSTOMER", customer, h.cash));
    var gate = new CountDownLatch(1);
    String path = "/api/v1/accounting/parties/" + id + "/terms";
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return h.actAs(path, terms(1, 45, "20000"), "admin", "SUPER_ADMIN");
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return h.actAs(path, terms(1, 60, "30000"), "admin", "SUPER_ADMIN");
              });
      gate.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  void oneLedgerAccountCannotBeSharedAcrossCustomers() throws Exception {
    String other =
        h.h.id(
            h.h.create("/api/v1/customers", Map.of("name", "Other buyer", "mobile", "9876543211")));
    create(profile("CUSTOMER", customer, h.cash));
    action("/api/v1/accounting/parties", profile("CUSTOMER", other, h.cash), 409);
  }

  @Test
  void inactiveSupplierCanKeepFinancialRecordsForHistoricalSettlement() throws Exception {
    mvc.perform(
            patch("/api/v1/suppliers/" + supplier + "/status")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("active", false))))
        .andExpect(status().isOk());
    String account = h.account("LIABILITY"), id = create(profile("SUPPLIER", supplier, account));
    postTo(account, false, "20");
    assertThat(
            getJson("/api/v1/accounting/parties/" + id + "/ledger?from=" + h.start + "&to=" + h.end)
                .read("$.balance_kind", String.class))
        .isEqualTo("PAYABLE");
  }

  @Test
  void concurrentBindingsCannotShareOneLedgerAccount() throws Exception {
    String other =
        h.h.id(
            h.h.create(
                "/api/v1/customers", Map.of("name", "Concurrent buyer", "mobile", "9876543211")));
    var a = profile("CUSTOMER", customer, h.cash);
    var b = profile("CUSTOMER", other, h.cash);
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                gate.await();
                return h.actAs("/api/v1/accounting/parties", a, "admin", "SUPER_ADMIN");
              });
      var second =
          pool.submit(
              () -> {
                gate.await();
                return h.actAs("/api/v1/accounting/parties", b, "admin", "SUPER_ADMIN");
              });
      gate.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  @Test
  void termsEventsCannotBeReusedByDifferentActorsAndCreditLimitCanBeCleared() throws Exception {
    String id = create(profile("CUSTOMER", customer, h.cash));
    String path = "/api/v1/accounting/parties/" + id + "/terms";
    var first = terms(1, 45, "20000");
    action(path, first, 200);
    assertThat(h.actAs(path, first, "other-admin", "SUPER_ADMIN")).isEqualTo(409);
    action(path, terms(2, 45, null), 200);
    assertThat(action(path, first, 200).read("$.version", Integer.class)).isEqualTo(3);
    assertThat(
            db.queryForObject(
                "select credit_limit from party_financial_profiles where id=?",
                BigDecimal.class,
                UUID.fromString(id)))
        .isNull();
    assertThat(getJson("/api/v1/accounting/parties/" + id).read("$.terms_history", List.class))
        .hasSize(2);
  }
}
