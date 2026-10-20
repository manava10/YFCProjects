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
class MoneyJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  PartyFinanceJourneyTest f;
  AccountingJourneyTest h;
  String customer, supplier, bank, cash;

  @BeforeEach
  void setup() throws Exception {
    f = new PartyFinanceJourneyTest();
    f.mvc = mvc;
    f.json = json;
    f.db = db;
    f.setup();
    h = f.h;
    customer = f.create(f.profile("CUSTOMER", f.customer, h.cash));
    supplier = f.create(f.profile("SUPPLIER", f.supplier, h.account("LIABILITY")));
    bank = register("BANK");
    cash = register("CASH");
  }

  String uid() {
    return UUID.randomUUID().toString();
  }

  String register(String kind) throws Exception {
    String id = h.account("ASSET");
    h.h.create("/api/v1/accounting/money-accounts", registration(id, kind));
    return id;
  }

  Map<String, Object> registration(String id, String kind) {
    return Map.of(
        "accountId",
        id,
        "branchId",
        h.branch,
        "kind",
        kind,
        "reference",
        "Accountant verified cash/bank book designation");
  }

  Map<String, Object> voucher(String kind, String money, String amount) {
    return new HashMap<>(
        Map.of(
            "voucherId",
            uid(),
            "profileId",
            kind.startsWith("CUSTOMER") ? customer : supplier,
            "moneyAccountId",
            money,
            "periodId",
            h.period,
            "branchId",
            h.branch,
            "kind",
            kind,
            "entryDate",
            h.date.toString(),
            "amount",
            new BigDecimal(amount),
            "reference",
            uid(),
            "evidenceReference",
            "Bank confirmation or signed cash receipt"));
  }

  String create(Map<String, Object> r) throws Exception {
    h.h.create("/api/v1/accounting/money-vouchers", r);
    return (String) r.get("voucherId");
  }

  int decide(String id, String action, Map<String, Object> event) throws Exception {
    return h.actAs(
        "/api/v1/accounting/money-vouchers/" + id + "/" + action,
        event,
        "ledger-reviewer",
        "ACCOUNTS");
  }

  void postVoucher(String id) throws Exception {
    assertThat(decide(id, "post", h.decision())).isEqualTo(200);
  }

  BigDecimal balance(String account) {
    return db.queryForObject(
        "select coalesce(sum(l.debit-l.credit),0) from journal_lines l join journals j on"
            + " j.id=l.journal_id where l.account_id=? and j.status='POSTED'",
        BigDecimal.class,
        UUID.fromString(account));
  }

  @Test
  void customerReceiptCreatesOneBalancedJournalWithIndependentPosting() throws Exception {
    var r = voucher("CUSTOMER_RECEIPT", bank, "123.45");
    String id = create(r);
    assertThat(balance(bank)).isZero();
    assertThat(
            h.actAs(
                "/api/v1/accounting/money-vouchers/" + id + "/post",
                h.decision(),
                "ledger-submitter",
                "SUPER_ADMIN"))
        .isEqualTo(409);
    var event = h.decision();
    assertThat(decide(id, "post", event)).isEqualTo(200);
    assertThat(decide(id, "post", event)).isEqualTo(200);
    h.action("/api/v1/accounting/money-vouchers", r, 200);
    assertThat(balance(bank)).isEqualByComparingTo("123.45");
    assertThat(balance(h.cash)).isEqualByComparingTo("-123.45");
    var result = h.getJson("/api/v1/accounting/money-vouchers/" + id);
    assertThat(result.read("$.effective_status", String.class)).isEqualTo("POSTED");
    assertThat(result.read("$.bank_transfer_executed_by_system", Boolean.class)).isFalse();
    assertThat(
            db.queryForObject(
                "select count(*) from journals where id=?", Integer.class, UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void supplierPaymentsAllowAdvancesAndRefundsAreCapped() throws Exception {
    postVoucher(create(voucher("SUPPLIER_PAYMENT", bank, "50")));
    String refund = create(voucher("SUPPLIER_REFUND", bank, "51"));
    assertThat(decide(refund, "post", h.decision())).isEqualTo(409);
    assertThat(h.review(refund, "post", h.decision())).isEqualTo(409);
    postVoucher(create(voucher("SUPPLIER_REFUND", bank, "20")));
    assertThat(balance(bank)).isEqualByComparingTo("-30");
    postVoucher(create(voucher("CUSTOMER_RECEIPT", bank, "100")));
    postVoucher(create(voucher("CUSTOMER_REFUND", bank, "70")));
    String tooMuch = create(voucher("CUSTOMER_REFUND", bank, "31"));
    assertThat(decide(tooMuch, "post", h.decision())).isEqualTo(409);
    assertThat(balance(h.cash)).isEqualByComparingTo("-30");
  }

  @Test
  void duplicateReferencesReserveDraftsAndRejectionReleasesClaim() throws Exception {
    var r = voucher("CUSTOMER_RECEIPT", bank, "10");
    r.put("reference", " Ref-001 ");
    String id = create(r);
    var other = voucher("CUSTOMER_RECEIPT", bank, "10");
    other.put("reference", "ref-001");
    h.action("/api/v1/accounting/money-vouchers", other, 409);
    assertThat(h.review(id, "reject", h.decision())).isEqualTo(200);
    create(other);
    assertThat(balance(bank)).isZero();
    r.put("amount", new BigDecimal("11"));
    h.action("/api/v1/accounting/money-vouchers", r, 409);
  }

  @Test
  void genericJournalPostAndReversalStayInSyncWithVoucher() throws Exception {
    String id = create(voucher("CUSTOMER_RECEIPT", bank, "10"));
    h.postJournal(id);
    var reversal = h.reversal();
    h.h.create("/api/v1/accounting/money-vouchers/" + id + "/reversals", reversal);
    h.postJournal((String) reversal.get("journalId"));
    assertThat(
            h.getJson("/api/v1/accounting/money-vouchers/" + id)
                .read("$.effective_status", String.class))
        .isEqualTo("REVERSED");
    assertThat(balance(bank)).isZero();
    var duplicate = voucher("CUSTOMER_RECEIPT", bank, "10");
    duplicate.put(
        "reference",
        db.queryForObject(
            "select reference from money_vouchers where id=?", String.class, UUID.fromString(id)));
    h.action("/api/v1/accounting/money-vouchers", duplicate, 409);
  }

  @Test
  void cashCannotBecomeNegativeIncludingBackdatedAndManualJournals() throws Exception {
    String unfunded = create(voucher("SUPPLIER_PAYMENT", cash, "1"));
    assertThat(decide(unfunded, "post", h.decision())).isEqualTo(409);
    String received = create(voucher("CUSTOMER_RECEIPT", cash, "100"));
    postVoucher(received);
    var spend = voucher("SUPPLIER_PAYMENT", cash, "100");
    spend.put("entryDate", h.date.plusDays(2).toString());
    postVoucher(create(spend));
    var backdated = voucher("SUPPLIER_PAYMENT", cash, "1");
    backdated.put("entryDate", h.date.plusDays(1).toString());
    String id = create(backdated);
    assertThat(decide(id, "post", h.decision())).isEqualTo(409);
    var manual = h.journalBody("1");
    manual.put("lines", List.of(h.line(h.equity, "1", "0"), h.line(cash, "0", "1")));
    String journal = h.submit(manual);
    assertThat(h.review(journal, "post", h.decision())).isEqualTo(409);
    assertThat(balance(cash)).isZero();
    var reversal = h.reversal();
    h.h.create("/api/v1/accounting/money-vouchers/" + received + "/reversals", reversal);
    assertThat(h.review((String) reversal.get("journalId"), "post", h.decision())).isEqualTo(409);
    assertThat(
            h.getJson("/api/v1/accounting/money-vouchers/" + received)
                .read("$.effective_status", String.class))
        .isEqualTo("POSTED");
  }

  @Test
  void sourcePartyBranchesAndInactiveAccountsAreValidated() throws Exception {
    var mismatch = voucher("CUSTOMER_RECEIPT", bank, "10");
    mismatch.put("profileId", supplier);
    h.action("/api/v1/accounting/money-vouchers", mismatch, 409);
    mismatch = voucher("CUSTOMER_RECEIPT", bank, "10");
    mismatch.put("branchId", uid());
    h.action("/api/v1/accounting/money-vouchers", mismatch, 409);
    var manual = h.journalBody("1");
    manual.put("branchId", uid());
    manual.put("lines", List.of(h.line(bank, "1", "0"), h.line(h.equity, "0", "1")));
    h.action("/api/v1/accounting/journals", manual, 400);
    String otherBranch =
        h.h.id(
            h.h.create(
                "/api/v1/branches",
                Map.of(
                    "code",
                    h.code(),
                    "name",
                    "Other cash branch",
                    "address",
                    "Office",
                    "city",
                    "Gurgaon",
                    "state",
                    "Haryana",
                    "pincode",
                    "122001")));
    manual.put("branchId", otherBranch);
    h.action("/api/v1/accounting/journals", manual, 409);
    String id = create(voucher("CUSTOMER_RECEIPT", bank, "10"));
    mvc.perform(
            patch("/api/v1/accounting/accounts/" + bank + "/status")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "expectedActive", true, "active", false, "reason", "Account frozen"))))
        .andExpect(status().isOk());
    assertThat(decide(id, "post", h.decision())).isEqualTo(409);
    assertThat(decide(id, "reject", h.decision())).isEqualTo(200);
  }

  @Test
  void cashBankAndPartyMappingsCannotBeConfusedOrReclassified() throws Exception {
    h.action("/api/v1/accounting/money-accounts", registration(h.cash, "BANK"), 409);
    h.action("/api/v1/accounting/parties", f.profile("CUSTOMER", f.customer, bank), 409);
    h.action("/api/v1/accounting/money-accounts", registration(bank, "BANK"), 200);
    h.action("/api/v1/accounting/money-accounts", registration(bank, "CASH"), 409);
    String used = h.account("ASSET");
    var manual = h.journalBody("1");
    manual.put("lines", List.of(h.line(used, "1", "0"), h.line(h.equity, "0", "1")));
    h.submit(manual);
    h.action("/api/v1/accounting/money-accounts", registration(used, "BANK"), 409);
  }

  @Test
  void datesEvidencePrecisionAndPermissionsAreEnforced() throws Exception {
    var r = voucher("CUSTOMER_RECEIPT", bank, "0");
    h.action("/api/v1/accounting/money-vouchers", r, 400);
    r = voucher("CUSTOMER_RECEIPT", bank, "0.001");
    h.action("/api/v1/accounting/money-vouchers", r, 400);
    r = voucher("CUSTOMER_RECEIPT", bank, "10");
    r.put("evidenceReference", " ");
    h.action("/api/v1/accounting/money-vouchers", r, 400);
    r = voucher("CUSTOMER_RECEIPT", bank, "10");
    r.put("entryDate", h.end.plusDays(1).toString());
    h.action("/api/v1/accounting/money-vouchers", r, 400);
    for (String role : List.of("OPERATIONS", "CUSTOMER", "SUPPLIER", "DRIVER"))
      mvc.perform(get("/api/v1/accounting/money-vouchers").with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    assertThat(
            h.actAs(
                "/api/v1/accounting/money-accounts",
                registration(bank, "BANK"),
                "accounts",
                "ACCOUNTS"))
        .isEqualTo(403);
    mvc.perform(
            post("/api/v1/accounting/money-vouchers")
                .contentType("application/json")
                .content(json.writeValueAsString(voucher("CUSTOMER_RECEIPT", bank, "10"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void concurrentRefundsCannotConsumeTheSameCustomerCredit() throws Exception {
    postVoucher(create(voucher("CUSTOMER_RECEIPT", bank, "10")));
    String a = create(voucher("CUSTOMER_REFUND", bank, "10")),
        b = create(voucher("CUSTOMER_REFUND", bank, "10"));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                gate.await();
                return decide(a, "post", h.decision());
              });
      var second =
          pool.submit(
              () -> {
                gate.await();
                return decide(b, "post", h.decision());
              });
      gate.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(balance(h.cash)).isZero();
  }

  @Test
  void voucherIdCannotAdoptAnExistingManualJournal() throws Exception {
    String id = h.submit(h.journalBody("10"));
    var r = voucher("CUSTOMER_RECEIPT", bank, "10");
    r.put("voucherId", id);
    h.action("/api/v1/accounting/money-vouchers", r, 409);
  }

  @Test
  void concurrentCashPaymentsCannotSpendTheSameFunds() throws Exception {
    postVoucher(create(voucher("CUSTOMER_RECEIPT", cash, "10")));
    String a = create(voucher("SUPPLIER_PAYMENT", cash, "10")),
        b = create(voucher("SUPPLIER_PAYMENT", cash, "10"));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                gate.await();
                return decide(a, "post", h.decision());
              });
      var second =
          pool.submit(
              () -> {
                gate.await();
                return decide(b, "post", h.decision());
              });
      gate.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(balance(cash)).isZero();
  }
}
