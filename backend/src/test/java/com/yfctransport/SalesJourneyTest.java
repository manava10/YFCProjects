package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
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
@WithMockUser(username = "sales-admin", roles = "SUPER_ADMIN")
class SalesJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  String code() {
    return "S" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Kolkata"));
  }

  DocumentContext create(String path, Object body) throws Exception {
    return JsonPath.parse(
        mvc.perform(
                post(path)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  DocumentContext action(String path, Object body, int status) throws Exception {
    var request = post(path).with(csrf());
    if (body != null)
      request.contentType("application/json").content(json.writeValueAsString(body));
    return JsonPath.parse(
        mvc.perform(request)
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  String id(DocumentContext document) {
    return document.read("$.id", String.class);
  }

  record Fixture(
      String customer, String branch, String project, String site, String first, String second) {}

  Fixture fixture() throws Exception {
    String customer =
        id(create("/api/v1/customers", Map.of("name", "Sales Buyer", "mobile", "9876543210")));
    String branch =
        id(
            create(
                "/api/v1/branches",
                Map.of(
                    "code",
                    code(),
                    "name",
                    "Sales branch",
                    "address",
                    "Office",
                    "city",
                    "Gurgaon",
                    "state",
                    "Haryana",
                    "pincode",
                    "122001")));
    String project =
        id(
            create(
                "/api/v1/customers/" + customer + "/projects",
                Map.of("code", code(), "name", "Road Project", "branchId", branch)));
    String site =
        id(
            create(
                "/api/v1/customers/" + customer + "/projects/" + project + "/sites",
                Map.of("code", code(), "name", "Site A", "address", "Agreed site address")));
    String first =
        id(
            create(
                "/api/v1/materials",
                Map.of("code", code(), "name", "20 MM Aggregate", "category", "AGGREGATES")));
    String second =
        id(
            create(
                "/api/v1/materials", Map.of("code", code(), "name", "M Sand", "category", "SAND")));
    return new Fixture(customer, branch, project, site, first, second);
  }

  Map<String, Object> rfqBody(
      Fixture f, String business, String firstQuantity, String secondQuantity) {
    return Map.of(
        "customerId",
        f.customer(),
        "deliverySiteId",
        f.site(),
        "businessType",
        business,
        "requiredDate",
        today().plusDays(3).toString(),
        "remarks",
        "Supply to site",
        "items",
        List.of(
            Map.of("materialId", f.first(), "quantity", new BigDecimal(firstQuantity)),
            Map.of("materialId", f.second(), "quantity", new BigDecimal(secondQuantity))));
  }

  DocumentContext rfq(Fixture f) throws Exception {
    return create("/api/v1/rfqs", rfqBody(f, "MATERIAL_AND_TRANSPORT", "100", "50"));
  }

  Map<String, Object> quoteBody(
      DocumentContext rfq,
      String material1,
      String transport1,
      String material2,
      String transport2) {
    return Map.of(
        "validUntil",
        today().plusDays(7).toString(),
        "paymentTerms",
        "Payment within 30 days",
        "deliveryTerms",
        "Customer pays loading/unloading; supplier pays royalty",
        "items",
        List.of(
            Map.of(
                "rfqItemId",
                rfq.read("$.items[0].id", String.class),
                "materialRate",
                new BigDecimal(material1),
                "transportRate",
                new BigDecimal(transport1)),
            Map.of(
                "rfqItemId",
                rfq.read("$.items[1].id", String.class),
                "materialRate",
                new BigDecimal(material2),
                "transportRate",
                new BigDecimal(transport2))));
  }

  DocumentContext quote(DocumentContext rfq) throws Exception {
    return create(
        "/api/v1/rfqs/" + id(rfq) + "/quotations", quoteBody(rfq, "1200", "200", "1000", "100"));
  }

  DocumentContext send(DocumentContext quote) throws Exception {
    return action("/api/v1/quotations/" + id(quote) + "/send", null, 200);
  }

  DocumentContext accept(DocumentContext quote) throws Exception {
    return action(
        "/api/v1/quotations/" + id(quote) + "/accept",
        Map.of(
            "customerContact",
            "Site engineer",
            "approvalReference",
            "Customer PO / email reference",
            "note",
            "Ordered quantity billing agreed"),
        200);
  }

  @Test
  void multiMaterialQuotationConvertsOnceAndTracksWeightsPerOrderItem() throws Exception {
    var f = fixture();
    var rfq = rfq(f);
    var quote = quote(rfq);
    assertThat(quote.read("$.subtotal", Double.class)).isEqualTo(195000d);
    send(quote);
    var accepted = accept(quote);
    assertThat(accepted.read("$.recordedBy", String.class)).isEqualTo("sales-admin");
    assertThat(accepted.read("$.approvalReference", String.class))
        .isEqualTo("Customer PO / email reference");
    var converted = action("/api/v1/quotations/" + id(quote) + "/convert", null, 201);
    String order = converted.read("$.orderId", String.class);
    var replay = action("/api/v1/quotations/" + id(quote) + "/convert", null, 200);
    assertThat(replay.read("$.orderId", String.class)).isEqualTo(order);
    var result =
        mvc.perform(get("/api/v1/orders/" + order))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.subtotal").value(195000))
            .andExpect(jsonPath("$.orderedQuantity").value(150))
            .andExpect(jsonPath("$.paymentTerms").value("Payment within 30 days"))
            .andReturn();
    String line = JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
    String reference = code();
    mvc.perform(
            post("/api/v1/orders/" + order + "/dispatches")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "reference", reference, "loadedQuantity", 99, "receivedQuantity", 98))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/orders/" + order + "/dispatches")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "reference",
                            reference,
                            "loadedQuantity",
                            99,
                            "receivedQuantity",
                            98,
                            "orderItemId",
                            line))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].billableQuantity").value(100))
        .andExpect(jsonPath("$.items[0].receivedQuantity").value(98))
        .andExpect(jsonPath("$.items[1].receivedQuantity").value(0))
        .andExpect(jsonPath("$.subtotal").value(195000));
    assertThat(
            db.queryForObject(
                "select count(*) from sales_orders where source_rfq_id=?",
                Integer.class,
                UUID.fromString(id(rfq))))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "select count(*) from audit_events where action='QUOTATION_CONVERTED' and"
                    + " target_id=?",
                Integer.class,
                UUID.fromString(order)))
        .isEqualTo(1);
  }

  @Test
  void decimalComponentRoundingIsPreservedInTheOrder() throws Exception {
    var f = fixture();
    var rfq = create("/api/v1/rfqs", rfqBody(f, "MATERIAL_AND_TRANSPORT", "0.001", "2.345"));
    var quote =
        create(
            "/api/v1/rfqs/" + id(rfq) + "/quotations",
            quoteBody(rfq, "5.00", "5.00", "1234.56", "100.01"));
    assertThat(quote.read("$.items[0].subtotal", Double.class)).isEqualTo(0.02d);
    assertThat(quote.read("$.subtotal", Double.class)).isEqualTo(3129.58d);
    send(quote);
    accept(quote);
    String order =
        action("/api/v1/quotations/" + id(quote) + "/convert", null, 201)
            .read("$.orderId", String.class);
    mvc.perform(get("/api/v1/orders/" + order))
        .andExpect(jsonPath("$.subtotal").value(3129.58))
        .andExpect(jsonPath("$.items[0].subtotal").value(0.02));
  }

  @Test
  void largeAmountsRemainExactBeyondFloatingPointPrecision() throws Exception {
    var rfq =
        create(
            "/api/v1/rfqs", rfqBody(fixture(), "MATERIAL_AND_TRANSPORT", "999999999.999", "0.001"));
    var quote =
        create(
            "/api/v1/rfqs/" + id(rfq) + "/quotations",
            quoteBody(rfq, "9999999999.99", "9999999999.99", "1", "0"));
    assertThat(
            db.queryForObject(
                "select sum(material_amount+transport_amount) from quotation_items where"
                    + " quotation_id=?",
                BigDecimal.class,
                UUID.fromString(id(quote))))
        .isEqualByComparingTo("19999999999960000000.00");
    mvc.perform(get("/api/v1/quotations/" + id(quote)))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("19999999999960000000.00")));
  }

  @Test
  void newSentRevisionSupersedesOldQuotationAndCannotMutateAcceptedRfq() throws Exception {
    var rfq = rfq(fixture());
    var first = quote(rfq);
    send(first);
    var second = quote(rfq);
    mvc.perform(get("/api/v1/quotations/" + id(first)))
        .andExpect(jsonPath("$.status").value("SENT"));
    send(second);
    mvc.perform(
            post("/api/v1/quotations/" + id(first) + "/accept")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("customerContact", "Engineer", "approvalReference", "PO-1"))))
        .andExpect(status().isConflict());
    accept(second);
    mvc.perform(
            post("/api/v1/rfqs/" + id(rfq) + "/quotations")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(quoteBody(rfq, "1", "0", "1", "0"))))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/rfqs/" + id(rfq) + "/quotations"))
        .andExpect(jsonPath("$[0].revision").value(2))
        .andExpect(jsonPath("$[1].status").value("SUPERSEDED"));
  }

  @Test
  void customerCanAcceptPreviouslySentQuoteWhileNewRevisionIsStillDraft() throws Exception {
    var rfq = rfq(fixture());
    var first = quote(rfq);
    send(first);
    var draft = quote(rfq);
    accept(first);
    mvc.perform(get("/api/v1/quotations/" + id(draft)))
        .andExpect(jsonPath("$.status").value("SUPERSEDED"));
    mvc.perform(post("/api/v1/quotations/" + id(draft) + "/send").with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void draftsCannotBeAcceptedOrConvertedAndExpiryPreventsAcceptance() throws Exception {
    var rfq = rfq(fixture());
    var quote = quote(rfq);
    mvc.perform(post("/api/v1/quotations/" + id(quote) + "/convert").with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/quotations/" + id(quote) + "/accept")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("customerContact", "Engineer", "approvalReference", "PO-1"))))
        .andExpect(status().isConflict());
    send(quote);
    db.update(
        "update quotations set valid_until=? where id=?",
        today().minusDays(1),
        UUID.fromString(id(quote)));
    mvc.perform(
            post("/api/v1/quotations/" + id(quote) + "/accept")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("customerContact", "Engineer", "approvalReference", "PO-1"))))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectionRequiresReasonAndEnablesANewRevision() throws Exception {
    var rfq = rfq(fixture());
    var quote = quote(rfq);
    send(quote);
    mvc.perform(
            post("/api/v1/quotations/" + id(quote) + "/reject")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "customerContact",
                            "Engineer",
                            "approvalReference",
                            "Call",
                            "reason",
                            ""))))
        .andExpect(status().isBadRequest());
    var rejected =
        action(
            "/api/v1/quotations/" + id(quote) + "/reject",
            Map.of(
                "customerContact",
                "Engineer",
                "approvalReference",
                "Call",
                "reason",
                "Rate not accepted"),
            200);
    assertThat(rejected.read("$.decisionNote", String.class)).isEqualTo("Rate not accepted");
    assertThat(quote(rfq).read("$.revision", Integer.class)).isEqualTo(2);
  }

  @Test
  void missingDuplicateAndForeignLinePricesAreRejectedAtomically() throws Exception {
    var rfq = rfq(fixture());
    var body = new HashMap<>(quoteBody(rfq, "1", "1", "1", "1"));
    body.put(
        "items",
        List.of(
            Map.of(
                "rfqItemId",
                rfq.read("$.items[0].id", String.class),
                "materialRate",
                1,
                "transportRate",
                1)));
    mvc.perform(
            post("/api/v1/rfqs/" + id(rfq) + "/quotations")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    var bad = Map.of("rfqItemId", UUID.randomUUID(), "materialRate", 1, "transportRate", 1);
    body.put("items", List.of(bad, bad));
    mvc.perform(
            post("/api/v1/rfqs/" + id(rfq) + "/quotations")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    assertThat(
            db.queryForObject(
                "select count(*) from quotations where rfq_id=?",
                Integer.class,
                UUID.fromString(id(rfq))))
        .isZero();
  }

  @Test
  void inputPrecisionAndBusinessTypeChargeRulesAreEnforced() throws Exception {
    var f = fixture();
    var body = new HashMap<>(rfqBody(f, "MATERIAL", "1", "2"));
    body.put(
        "items", List.of(Map.of("materialId", f.first(), "quantity", new BigDecimal("1.0001"))));
    mvc.perform(
            post("/api/v1/rfqs")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    var rfq = create("/api/v1/rfqs", rfqBody(f, "MATERIAL", "1", "2"));
    mvc.perform(
            post("/api/v1/rfqs/" + id(rfq) + "/quotations")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(quoteBody(rfq, "1", "1", "1", "0"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/rfqs/" + id(rfq) + "/quotations")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(quoteBody(rfq, "1.001", "0", "1", "0"))))
        .andExpect(status().isBadRequest());
    assertThat(
            create("/api/v1/rfqs/" + id(rfq) + "/quotations", quoteBody(rfq, "1", "0", "1", "0"))
                .read("$.revision", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void conversionUsesAgreedSnapshotsAndRechecksActiveRecords() throws Exception {
    var f = fixture();
    var rfq = rfq(f);
    var quote = quote(rfq);
    send(quote);
    accept(quote);
    mvc.perform(
            put("/api/v1/materials/" + f.first())
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "code",
                            code(),
                            "name",
                            "New material label",
                            "category",
                            "AGGREGATES"))))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/v1/customers/"
                    + f.customer()
                    + "/projects/"
                    + f.project()
                    + "/sites/"
                    + f.site())
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "code", code(), "name", "New label", "address", "New site address"))))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/v1/materials/" + f.first() + "/status")
                .with(csrf())
                .contentType("application/json")
                .content("{\"active\":false}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/quotations/" + id(quote) + "/convert").with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/v1/materials/" + f.first() + "/status")
                .with(csrf())
                .contentType("application/json")
                .content("{\"active\":true}"))
        .andExpect(status().isOk());
    db.update(
        "update quotations set valid_until=? where id=?",
        today().minusDays(1),
        UUID.fromString(id(quote)));
    String order =
        action("/api/v1/quotations/" + id(quote) + "/convert", null, 201)
            .read("$.orderId", String.class);
    mvc.perform(get("/api/v1/orders/" + order))
        .andExpect(jsonPath("$.deliverySite").value("Agreed site address"))
        .andExpect(jsonPath("$.items[0].material").value("20 MM Aggregate"))
        .andExpect(jsonPath("$.requiredDate").value(today().plusDays(3).toString()));
  }

  @Test
  void concurrentConversionCreatesOnlyOneOrder() throws Exception {
    var rfq = rfq(fixture());
    var quote = quote(rfq);
    send(quote);
    accept(quote);
    java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult> attempt =
        () ->
            mvc.perform(
                    post("/api/v1/quotations/" + id(quote) + "/convert")
                        .with(user("operations").roles("OPERATIONS"))
                        .with(csrf()))
                .andReturn();
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executor.invokeAll(List.of(attempt, attempt));
      var a = results.get(0).get().getResponse();
      var b = results.get(1).get().getResponse();
      assertThat(List.of(a.getStatus(), b.getStatus())).containsExactlyInAnyOrder(201, 200);
      assertThat(JsonPath.read(a.getContentAsString(), "$.orderId").toString())
          .isEqualTo(JsonPath.read(b.getContentAsString(), "$.orderId").toString());
    }
    assertThat(
            db.queryForObject(
                "select count(*) from sales_orders where source_rfq_id=?",
                Integer.class,
                UUID.fromString(id(rfq))))
        .isEqualTo(1);
  }

  @Test
  void simultaneousRevisionsHaveUniqueSequentialNumbers() throws Exception {
    var rfq = rfq(fixture());
    String payload = json.writeValueAsString(quoteBody(rfq, "1", "1", "2", "2"));
    java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult> attempt =
        () ->
            mvc.perform(
                    post("/api/v1/rfqs/" + id(rfq) + "/quotations")
                        .with(user("operations").roles("OPERATIONS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(payload))
                .andReturn();
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executor.invokeAll(List.of(attempt, attempt));
      var a = results.get(0).get().getResponse();
      var b = results.get(1).get().getResponse();
      assertThat(a.getStatus()).isEqualTo(201);
      assertThat(b.getStatus()).isEqualTo(201);
      assertThat(
              List.of(
                  JsonPath.read(a.getContentAsString(), "$.revision").toString(),
                  JsonPath.read(b.getContentAsString(), "$.revision").toString()))
          .containsExactlyInAnyOrder("1", "2");
    }
  }

  @Test
  void rfqRejectsDuplicateMaterialsAndSitesFromAnotherCustomer() throws Exception {
    var f = fixture();
    var body = new HashMap<>(rfqBody(f, "MATERIAL", "1", "1"));
    body.put(
        "items",
        List.of(
            Map.of("materialId", f.first(), "quantity", 1),
            Map.of("materialId", f.first(), "quantity", 1)));
    mvc.perform(
            post("/api/v1/rfqs")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    var other =
        id(create("/api/v1/customers", Map.of("name", "Different buyer", "mobile", "9876543210")));
    body = new HashMap<>(rfqBody(f, "MATERIAL", "1", "1"));
    body.put("customerId", other);
    mvc.perform(
            post("/api/v1/rfqs")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isNotFound());
    body = new HashMap<>(rfqBody(f, "MATERIAL", "1", "1"));
    body.put("requiredDate", today().minusDays(1).toString());
    mvc.perform(
            post("/api/v1/rfqs")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void transportOnlyQuotationCannotChargeForMaterials() throws Exception {
    var rfq = create("/api/v1/rfqs", rfqBody(fixture(), "TRANSPORT", "10", "20"));
    mvc.perform(
            post("/api/v1/rfqs/" + id(rfq) + "/quotations")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(quoteBody(rfq, "1", "10", "0", "20"))))
        .andExpect(status().isBadRequest());
    var quote =
        create("/api/v1/rfqs/" + id(rfq) + "/quotations", quoteBody(rfq, "0", "10", "0", "20"));
    assertThat(quote.read("$.materialTotal", Double.class)).isZero();
    assertThat(quote.read("$.subtotal", Double.class)).isEqualTo(500d);
  }

  @Test
  void permissionsAndCsrfProtectSalesActions() throws Exception {
    var f = fixture();
    var rfq = rfq(f);
    var quote = quote(rfq);
    mvc.perform(get("/api/v1/rfqs/" + id(rfq)).with(user("accounts").roles("ACCOUNTS")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/quotations/" + id(quote) + "/send")
                .with(user("accounts").roles("ACCOUNTS"))
                .with(csrf()))
        .andExpect(status().isForbidden());
    for (String role : List.of("CUSTOMER", "SUPPLIER", "DRIVER"))
      mvc.perform(get("/api/v1/rfqs/" + id(rfq)).with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/quotations/" + id(quote) + "/send"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/quotations/" + id(quote) + "/accept")
                .with(csrf())
                .contentType("application/json")
                .content("{\"customerContact\":\"Engineer\"}"))
        .andExpect(status().isBadRequest());
  }
}
