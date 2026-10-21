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
@WithMockUser(username = "billing-preparer", roles = "SUPER_ADMIN")
class BillingPreparationJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  InventoryJourneyTest h;
  InventoryJourneyTest.Fixture f;
  static final String API = "/api/v1/accounting/billing-preparations";

  @BeforeEach
  void setup() throws Exception {
    h = new InventoryJourneyTest();
    h.mvc = mvc;
    h.json = json;
    h.db = db;
    h.setup();
    f = h.fixture();
  }

  String uid() {
    return UUID.randomUUID().toString();
  }

  Map<String, Object> body(String kind, String source, String basis) {
    return new HashMap<>(
        Map.of(
            "preparationId",
            uid(),
            "kind",
            kind,
            "sourceId",
            source,
            "preparedOn",
            h.helpers.today().toString(),
            "quantityBasis",
            basis,
            "reference",
            uid(),
            "evidenceReference",
            "Accepted quotation / supplier agreement reference",
            "basisReason",
            "Agreed quantity basis checked against the source records"));
  }

  Map<String, Object> sale() {
    return body("SALES_ORDER", f.order(), "ORDERED");
  }

  String create(Map<String, Object> body) throws Exception {
    h.create(API, body);
    return (String) body.get("preparationId");
  }

  DocumentContext getOne(String id) throws Exception {
    return JsonPath.parse(
        mvc.perform(get(API + "/" + id))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  Map<String, Object> event() {
    return Map.of(
        "eventId", uid(), "reason", "Commercial basis independently checked; taxes pending");
  }

  int review(String id, String action, Map<String, Object> event, String actor) throws Exception {
    return mvc.perform(
            post(API + "/" + id + "/" + action)
                .with(user(actor).roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(event)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  String receipt(String accepted) throws Exception {
    String p = h.purchase(f, "YARD", "10");
    var r = h.receipt("9", "8", accepted);
    h.receive(p, r, 201);
    return (String) r.get("receiptId");
  }

  @Test
  void orderedHundredIsPreservedDespiteNinetyNineLoadedAndNinetyEightReceived() throws Exception {
    h.action(
        "/api/v1/orders/" + f.order() + "/dispatches",
        Map.of("reference", h.helpers.code(), "loadedQuantity", 99, "receivedQuantity", 98),
        201);
    String id = create(sale());
    var result = getOne(id);
    assertThat(result.read("$.lines[0].quantity", BigDecimal.class)).isEqualByComparingTo("100");
    assertThat(result.read("$.lines[0].loaded_quantity", BigDecimal.class))
        .isEqualByComparingTo("99");
    assertThat(result.read("$.lines[0].received_quantity", BigDecimal.class))
        .isEqualByComparingTo("98");
    assertThat(result.read("$.subtotal", BigDecimal.class)).isEqualByComparingTo("120000");
    assertThat(result.read("$.invoice_issued", Boolean.class)).isFalse();
    assertThat(result.read("$.ledger_posted", Boolean.class)).isFalse();
    assertThat(result.read("$.tax_treatment", String.class))
        .isEqualTo("PENDING_ACCOUNTANT_CONFIGURATION");
  }

  @Test
  void everyQuotationLineUsesItsOwnRatesAndComponentRounding() throws Exception {
    var s = h.helpers;
    var rfq =
        s.create("/api/v1/rfqs", s.rfqBody(f.sales(), "MATERIAL_AND_TRANSPORT", "0.005", "0.005"));
    var quote =
        s.create("/api/v1/rfqs/" + s.id(rfq) + "/quotations", s.quoteBody(rfq, "1", "1", "1", "1"));
    String q = s.id(quote);
    s.send(quote);
    s.accept(quote);
    var order = s.action("/api/v1/quotations/" + q + "/convert", null, 201);
    String orderId = order.read("$.orderId", String.class);
    var r = body("SALES_ORDER", orderId, "ORDERED");
    var result = getOne(create(r));
    assertThat(result.read("$.lines", List.class)).hasSize(2);
    BigDecimal total =
        db.queryForObject(
            "select sum(material_amount+transport_amount) from sales_order_items where order_id=?",
            BigDecimal.class,
            UUID.fromString(orderId));
    assertThat(result.read("$.subtotal", BigDecimal.class)).isEqualByComparingTo(total);
    assertThat(total).isEqualByComparingTo("0.04");
    assertThat(result.read("$.source_snapshot.orderId", String.class)).isEqualTo(orderId);
  }

  int reviewReversal(String id, String action) throws Exception {
    return mvc.perform(
            post("/api/v1/purchase-receipt-reversals/" + id + "/" + action)
                .with(user("reviewer").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("eventId", uid(), "reason", "Physical record independently checked"))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  Map<String, Object> reversal() {
    return Map.of(
        "reversalId",
        uid(),
        "reason",
        "Receipt recorded against the wrong purchase",
        "evidenceReference",
        "Signed receipt reversal note");
  }

  @Test
  void pendingOrApprovedReceiptReversalBlocksBillingUntilRejected() throws Exception {
    String source = receipt("7");
    var approved = reversal();
    h.action("/api/v1/purchase-receipts/" + source + "/reversals", approved, 201);
    h.action(API, body("PURCHASE_RECEIPT", source, "ACCEPTED"), 409);
    assertThat(reviewReversal(approved.get("reversalId").toString(), "approve")).isEqualTo(200);
    h.action(API, body("PURCHASE_RECEIPT", source, "ACCEPTED"), 409);
    String other = receipt("7");
    var pending = reversal();
    h.action("/api/v1/purchase-receipts/" + other + "/reversals", pending, 201);
    h.action(API, body("PURCHASE_RECEIPT", other, "ACCEPTED"), 409);
    assertThat(reviewReversal(pending.get("reversalId").toString(), "reject")).isEqualTo(200);
    create(body("PURCHASE_RECEIPT", other, "ACCEPTED"));
  }

  @Test
  void supplierMustExplicitlyChooseTheReceiptWeightAndPreservesEvidence() throws Exception {
    String source = receipt("7");
    var r = body("PURCHASE_RECEIPT", source, "ACCEPTED");
    String id = create(r);
    var result = getOne(id);
    assertThat(result.read("$.lines[0].quantity", BigDecimal.class)).isEqualByComparingTo("7");
    assertThat(result.read("$.subtotal", BigDecimal.class)).isEqualByComparingTo("7003.85");
    assertThat(result.read("$.source_snapshot.acceptedQuantity", BigDecimal.class))
        .isEqualByComparingTo("7");
    assertThat(review(id, "reject", event(), "reviewer")).isEqualTo(200);
    r = body("PURCHASE_RECEIPT", source, "LOADED");
    id = create(r);
    assertThat(getOne(id).read("$.subtotal", BigDecimal.class)).isEqualByComparingTo("9004.95");
    assertThat(review(id, "reject", event(), "reviewer")).isEqualTo(200);
    id = create(body("PURCHASE_RECEIPT", source, "RECEIVED"));
    assertThat(getOne(id).read("$.subtotal", BigDecimal.class)).isEqualByComparingTo("8004.40");
  }

  @Test
  void independentReviewAndExactRetriesCreateNoFinancialEntries() throws Exception {
    int journals = db.queryForObject("select count(*) from journals", Integer.class);
    var r = sale();
    String id = create(r);
    h.action(API, r, 200);
    assertThat(review(id, "ready", event(), "billing-preparer")).isEqualTo(409);
    var e = event();
    assertThat(review(id, "ready", e, "reviewer")).isEqualTo(200);
    assertThat(review(id, "ready", e, "reviewer")).isEqualTo(200);
    assertThat(review(id, "ready", e, "other-reviewer")).isEqualTo(409);
    assertThat(getOne(id).read("$.status", String.class)).isEqualTo("READY");
    assertThat(getOne(id).read("$.events", List.class)).hasSize(1);
    assertThat(db.queryForObject("select count(*) from journals", Integer.class))
        .isEqualTo(journals);
    h.action(API, sale(), 409);
    r.put("evidenceReference", "Changed support");
    h.action(API, r, 409);
  }

  @Test
  void rejectionOrCancellationReleasesWholeSourceWithoutDeletingHistory() throws Exception {
    var r = sale();
    String first = create(r);
    assertThat(review(first, "reject", event(), "reviewer")).isEqualTo(200);
    String second = create(sale());
    assertThat(review(second, "ready", event(), "reviewer")).isEqualTo(200);
    assertThat(review(second, "reject", event(), "reviewer")).isEqualTo(409);
    var e = event();
    assertThat(review(second, "cancel", e, "reviewer")).isEqualTo(200);
    assertThat(review(second, "cancel", e, "reviewer")).isEqualTo(200);
    String third = create(sale());
    assertThat(getOne(first).read("$.status", String.class)).isEqualTo("REJECTED");
    assertThat(getOne(second).read("$.status", String.class)).isEqualTo("CANCELLED");
    assertThat(getOne(third).read("$.status", String.class)).isEqualTo("DRAFT");
    h.action(API, r, 200);
    assertThat(
            db.queryForObject(
                "select count(*) from billing_source_claims where source_id=?",
                Integer.class,
                UUID.fromString(f.order())))
        .isEqualTo(1);
  }

  @Test
  void factsAndTermsAreSnapshotsEvenWhenSourceMastersChange() throws Exception {
    String id = create(sale());
    String original = getOne(id).read("$.party_name", String.class);
    db.update(
        "update customers set name=? where id=?",
        "Renamed buyer",
        UUID.fromString(f.sales().customer()));
    h.action(
        "/api/v1/orders/" + f.order() + "/dispatches",
        Map.of("reference", h.helpers.code(), "loadedQuantity", 1, "receivedQuantity", 1),
        201);
    var result = getOne(id);
    assertThat(result.read("$.party_name", String.class)).isEqualTo(original);
    assertThat(result.read("$.source_snapshot.customerName", String.class)).isEqualTo(original);
    assertThat(result.read("$.lines[0].loaded_quantity", BigDecimal.class)).isZero();
    assertThat(result.read("$.lines[0].quantity", BigDecimal.class)).isEqualByComparingTo("100");
  }

  @Test
  void badBasesMissingSourcesAndInvalidChronologyAreRejected() throws Exception {
    var r = sale();
    r.put("quantityBasis", "RECEIVED");
    h.action(API, r, 400);
    r = sale();
    r.put("sourceId", uid());
    h.action(API, r, 404);
    r = sale();
    r.put("preparedOn", h.helpers.today().plusDays(1).toString());
    h.action(API, r, 400);
    r = sale();
    r.put("preparedOn", h.helpers.today().minusDays(1).toString());
    h.action(API, r, 400);
    r = body("PURCHASE_RECEIPT", receipt("0"), "ACCEPTED");
    h.action(API, r, 409);
    r.put("quantityBasis", "ORDERED");
    h.action(API, r, 400);
    r.put("quantityBasis", "LOADED");
    create(r);
  }

  @Test
  void permissionsCsrfEvidenceAndPaginationAreEnforced() throws Exception {
    for (String role : List.of("OPERATIONS", "CUSTOMER", "SUPPLIER", "DRIVER")) {
      mvc.perform(get(API).with(user("external").roles(role))).andExpect(status().isForbidden());
      mvc.perform(
              post(API + "/" + uid() + "/ready")
                  .with(user("external").roles(role))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsString(event())))
          .andExpect(status().isForbidden());
      mvc.perform(
              post(API)
                  .with(user("external").roles(role))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsString(sale())))
          .andExpect(status().isForbidden());
    }
    mvc.perform(post(API).contentType("application/json").content(json.writeValueAsString(sale())))
        .andExpect(status().isForbidden());
    var r = sale();
    r.put("evidenceReference", " ");
    h.action(API, r, 400);
    r = sale();
    r.put("basisReason", " ");
    h.action(API, r, 400);
    mvc.perform(get(API + "?size=101")).andExpect(status().isBadRequest());
    mvc.perform(get(API + "?page=-1")).andExpect(status().isBadRequest());
    String id = create(sale());
    mvc.perform(
            get(
                API
                    + "?branchId="
                    + f.sales().branch()
                    + "&partyId="
                    + f.sales().customer()
                    + "&kind=SALES_ORDER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get(API + "?branchId=" + uid()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void eventIdsCannotBeReusedAcrossTargetsOrActions() throws Exception {
    String a = create(sale());
    var e = event();
    assertThat(review(a, "reject", e, "reviewer")).isEqualTo(200);
    String b = create(sale());
    assertThat(review(b, "ready", e, "reviewer")).isEqualTo(409);
    assertThat(review(a, "cancel", e, "reviewer")).isEqualTo(409);
    assertThat(getOne(b).read("$.status", String.class)).isEqualTo("DRAFT");
  }

  @Test
  void concurrentDraftsCannotBillTheSameSourceTwice() throws Exception {
    var a = sale();
    var b = sale();
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                gate.await();
                return submitAs(a);
              });
      var second =
          pool.submit(
              () -> {
                gate.await();
                return submitAs(b);
              });
      gate.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  int submitAs(Map<String, Object> r) throws Exception {
    return mvc.perform(
            post(API)
                .with(user("billing-preparer").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(r)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void concurrentReviewHasOnlyOneWinnerAndOneEvent() throws Exception {
    String id = create(sale());
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                gate.await();
                return review(id, "ready", event(), "reviewer-a");
              });
      var second =
          pool.submit(
              () -> {
                gate.await();
                return review(id, "reject", event(), "reviewer-b");
              });
      gate.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(getOne(id).read("$.events", List.class)).hasSize(1);
  }

  @Test
  void transportOnlyAndLegacyOrdersKeepTheirActualPricingAndBranchRules() throws Exception {
    var order =
        h.create(
            "/api/v1/orders",
            Map.of(
                "customerId",
                f.sales().customer(),
                "branchId",
                f.sales().branch(),
                "businessType",
                "TRANSPORT",
                "material",
                "Carried material",
                "deliverySite",
                "Site",
                "orderedQuantity",
                3,
                "unitRate",
                125));
    var result = getOne(create(body("SALES_ORDER", h.id(order), "ORDERED")));
    assertThat(result.read("$.lines[0].material_amount", BigDecimal.class)).isZero();
    assertThat(result.read("$.lines[0].transport_amount", BigDecimal.class))
        .isEqualByComparingTo("375");
    order =
        h.create(
            "/api/v1/orders",
            Map.of(
                "customerId",
                f.sales().customer(),
                "businessType",
                "MATERIAL",
                "material",
                "Legacy material",
                "deliverySite",
                "Site",
                "orderedQuantity",
                3,
                "unitRate",
                125));
    h.action(API, body("SALES_ORDER", h.id(order), "ORDERED"), 409);
  }

  @Test
  void inactiveBranchBlocksReadinessButStillAllowsRejection() throws Exception {
    String id = create(sale());
    db.update("update branches set active=false where id=?", UUID.fromString(f.sales().branch()));
    assertThat(review(id, "ready", event(), "reviewer")).isEqualTo(400);
    assertThat(review(id, "reject", event(), "reviewer")).isEqualTo(200);
    assertThat(getOne(id).read("$.status", String.class)).isEqualTo("REJECTED");
  }

  String account(String type) throws Exception {
    return h.id(
        h.create(
            "/api/v1/accounting/accounts",
            Map.of(
                "accountId",
                uid(),
                "code",
                "A" + uid().replace("-", "").substring(0, 15),
                "name",
                "Test " + type,
                "type",
                type)));
  }

  String periodCoveringToday() throws Exception {
    var existing =
        db.queryForList(
            "select id from financial_periods where starts_on<=? and ends_on>=? and status='OPEN'",
            h.helpers.today(),
            h.helpers.today());
    if (!existing.isEmpty()) return existing.getFirst().get("id").toString();
    var r =
        Map.of(
            "periodId",
            uid(),
            "code",
            "A" + uid().replace("-", "").substring(0, 15),
            "startsOn",
            h.helpers.today().toString(),
            "endsOn",
            h.helpers.today().plusDays(10).toString());
    h.create("/api/v1/accounting/periods", r);
    return r.get("periodId").toString();
  }

  @Test
  void readySalesPreparationPostsBeforeTaxToThePartyLedger() throws Exception {
    String income = account("INCOME");
    String asset = account("ASSET");
    h.create(
        "/api/v1/accounting/parties",
        Map.of(
            "profileId",
            uid(),
            "partyType",
            "CUSTOMER",
            "partyId",
            f.sales().customer(),
            "accountId",
            asset,
            "paymentTermsDays",
            30,
            "creditLimit",
            new BigDecimal("100000.00")));
    String period = periodCoveringToday();
    String id = create(sale());
    assertThat(review(id, "ready", event(), "reviewer")).isEqualTo(200);
    var journal =
        Map.of(
            "journalId",
            uid(),
            "periodId",
            period,
            "entryDate",
            h.helpers.today().toString(),
            "counterAccountId",
            income,
            "description",
            "Before-tax sales recognition from ordered quantity",
            "reference",
            uid());
    h.action(API + "/" + id + "/journals", journal, 201);
    h.action(API + "/" + id + "/journals", journal, 200);
    assertThat(getOne(id).read("$.ledger_posted", Boolean.class)).isFalse();
    assertThat(getOne(id).read("$.invoice_issued", Boolean.class)).isFalse();
    mvc.perform(
            post("/api/v1/accounting/journals/" + journal.get("journalId") + "/post")
                .with(user("ledger-reviewer").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "eventId",
                            uid(),
                            "reason",
                            "Party account and income mapping independently checked"))))
        .andExpect(status().isOk());
    assertThat(getOne(id).read("$.ledger_posted", Boolean.class)).isTrue();
    assertThat(getOne(id).read("$.journal_status", String.class)).isEqualTo("POSTED");
    assertThat(review(id, "cancel", event(), "reviewer")).isEqualTo(409);
  }

  void postJournal(String journalId) throws Exception {
    mvc.perform(
            post("/api/v1/accounting/journals/" + journalId + "/post")
                .with(user("ledger-reviewer").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "eventId",
                            uid(),
                            "reason",
                            "Party account and income mapping independently checked"))))
        .andExpect(status().isOk());
  }

  @Test
  void postedReversingJournalAllowsCancellationAndReplacement() throws Exception {
    String income = account("INCOME");
    String asset = account("ASSET");
    h.create(
        "/api/v1/accounting/parties",
        Map.of(
            "profileId",
            uid(),
            "partyType",
            "CUSTOMER",
            "partyId",
            f.sales().customer(),
            "accountId",
            asset,
            "paymentTermsDays",
            30,
            "creditLimit",
            new BigDecimal("100000.00")));
    String period = periodCoveringToday();
    String id = create(sale());
    assertThat(review(id, "ready", event(), "reviewer")).isEqualTo(200);
    var journal =
        Map.of(
            "journalId",
            uid(),
            "periodId",
            period,
            "entryDate",
            h.helpers.today().toString(),
            "counterAccountId",
            income,
            "description",
            "Before-tax sales recognition from ordered quantity",
            "reference",
            uid());
    h.action(API + "/" + id + "/journals", journal, 201);
    postJournal(journal.get("journalId").toString());
    var reversal =
        Map.of(
            "journalId",
            uid(),
            "periodId",
            period,
            "entryDate",
            h.helpers.today().toString(),
            "reason",
            "Correction approved from source records",
            "reference",
            uid());
    h.create("/api/v1/accounting/journals/" + journal.get("journalId") + "/reversals", reversal);
    assertThat(review(id, "cancel", event(), "reviewer")).isEqualTo(409);
    postJournal(reversal.get("journalId").toString());
    assertThat(review(id, "cancel", event(), "reviewer")).isEqualTo(200);
    assertThat(getOne(id).read("$.status", String.class)).isEqualTo("CANCELLED");
    String replacement = create(sale());
    assertThat(getOne(replacement).read("$.status", String.class)).isEqualTo("DRAFT");
  }

  @Test
  void postingRequiresReadyStatePartyProfileAndIncomeOrExpenseCounter() throws Exception {
    String id = create(sale());
    var journal =
        new HashMap<>(
            Map.of(
                "journalId",
                uid(),
                "periodId",
                uid(),
                "entryDate",
                h.helpers.today().toString(),
                "counterAccountId",
                uid(),
                "description",
                "Attempted posting",
                "reference",
                uid()));
    h.action(API + "/" + id + "/journals", journal, 409);
    assertThat(review(id, "ready", event(), "reviewer")).isEqualTo(200);
    h.action(API + "/" + id + "/journals", journal, 409);
  }

  @Test
  void failedAuditRollsBackPreparationAndSourceClaim() throws Exception {
    var r = sale();
    mvc.perform(
            post(API)
                .with(user("x".repeat(81)).roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(r)))
        .andExpect(status().isConflict());
    assertThat(
            db.queryForObject(
                "select count(*) from billing_source_claims where source_id=?",
                Integer.class,
                UUID.fromString(f.order())))
        .isZero();
    create(r);
  }
}
