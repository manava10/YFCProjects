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
@WithMockUser(username = "inventory-admin", roles = "SUPER_ADMIN")
class InventoryJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  SalesJourneyTest helpers;

  @BeforeEach
  void setup() {
    helpers = new SalesJourneyTest();
    helpers.mvc = mvc;
    helpers.json = json;
    helpers.db = db;
  }

  record Fixture(
      SalesJourneyTest.Fixture sales,
      String yard,
      String supplier,
      String plant,
      String order,
      String line) {}

  String id(DocumentContext d) {
    return helpers.id(d);
  }

  DocumentContext create(String path, Object body) throws Exception {
    return helpers.create(path, body);
  }

  DocumentContext action(String path, Object body, int status) throws Exception {
    var request = post(path).with(csrf());
    if (body != null)
      request.contentType("application/json").content(json.writeValueAsString(body));
    String result =
        mvc.perform(request)
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.parse(result.isBlank() ? "{}" : result);
  }

  Fixture fixture() throws Exception {
    var s = helpers.fixture();
    String yard =
        id(
            create(
                "/api/v1/yards",
                Map.of(
                    "branchId",
                    s.branch(),
                    "code",
                    helpers.code(),
                    "name",
                    "Main yard",
                    "address",
                    "Yard address")));
    // GST format is checked, uniqueness retained across fixtures without consuming a real tax
    // registration.
    String syntheticPan =
        UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 5)
            .chars()
            .mapToObj(c -> Character.toString((char) ('A' + Character.digit(c, 16))))
            .collect(java.util.stream.Collectors.joining());
    String gst =
        "06"
            + syntheticPan
            + String.format("%04d", Math.floorMod(UUID.randomUUID().hashCode(), 10000))
            + "F1Z"
            + helpers.code().toUpperCase(Locale.ROOT).charAt(1);
    String supplier =
        id(
            create(
                "/api/v1/suppliers",
                Map.of(
                    "code",
                    helpers.code(),
                    "name",
                    "Crusher",
                    "gstin",
                    gst,
                    "mobile",
                    "9876543210",
                    "address",
                    "Plant office")));
    String plant =
        id(
            create(
                "/api/v1/suppliers/" + supplier + "/plants",
                Map.of("code", helpers.code(), "name", "Crusher A", "address", "Plant address")));
    mvc.perform(
            put("/api/v1/suppliers/" + supplier + "/plants/" + plant + "/materials/" + s.first())
                .with(csrf()))
        .andExpect(status().isOk());
    var order =
        create(
            "/api/v1/orders",
            Map.of(
                "customerId",
                s.customer(),
                "deliverySiteId",
                s.site(),
                "materialId",
                s.first(),
                "businessType",
                "MATERIAL_AND_TRANSPORT",
                "orderedQuantity",
                new BigDecimal("100"),
                "materialRate",
                new BigDecimal("1200"),
                "transportRate",
                BigDecimal.ZERO));
    return new Fixture(
        s, yard, supplier, plant, id(order), order.read("$.items[0].id", String.class));
  }

  Map<String, Object> purchaseBody(Fixture f, String destination, String quantity) {
    var b = new HashMap<String, Object>();
    b.put("supplierId", f.supplier());
    b.put("plantId", f.plant());
    b.put("materialId", f.sales().first());
    b.put("destination", destination);
    b.put(
        destination.equals("YARD") ? "yardId" : "orderItemId",
        destination.equals("YARD") ? f.yard() : f.line());
    b.put("quantity", new BigDecimal(quantity));
    b.put("rate", new BigDecimal("1000.55"));
    b.put("expectedDate", helpers.today().plusDays(1).toString());
    b.put("terms", "Supplier pays royalty; customer pays loading/unloading");
    return b;
  }

  String purchase(Fixture f, String destination, String qty) throws Exception {
    return id(create("/api/v1/purchases", purchaseBody(f, destination, qty)));
  }

  Map<String, Object> receipt(String loaded, String received, String accepted) {
    return new HashMap<>(
        Map.of(
            "receiptId",
            UUID.randomUUID().toString(),
            "receivedDate",
            helpers.today().toString(),
            "loadedQuantity",
            new BigDecimal(loaded),
            "receivedQuantity",
            new BigDecimal(received),
            "acceptedQuantity",
            new BigDecimal(accepted),
            "reference",
            helpers.code(),
            "discrepancyReason",
            "Weighbridge/quality difference recorded"));
  }

  DocumentContext receive(String p, Map<String, Object> r, int expected) throws Exception {
    return action("/api/v1/purchases/" + p + "/receipts", r, expected);
  }

  void stock(Fixture f, String onHand, String reserved, String available) throws Exception {
    var text =
        mvc.perform(get("/api/v1/yards/" + f.yard() + "/stock/" + f.sales().first()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var parsed = JsonPath.parse(text);
    assertThat(new BigDecimal(parsed.read("$.on_hand").toString())).isEqualByComparingTo(onHand);
    assertThat(new BigDecimal(parsed.read("$.reserved").toString())).isEqualByComparingTo(reserved);
    assertThat(new BigDecimal(parsed.read("$.available").toString()))
        .isEqualByComparingTo(available);
  }

  String reserve(Fixture f, String qty) throws Exception {
    return id(
        create(
            "/api/v1/stock-reservations",
            Map.of("yardId", f.yard(), "orderItemId", f.line(), "quantity", new BigDecimal(qty))));
  }

  @Test
  void yardReceiptReservationPartialIssueAndReleasePreserveBilling() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "100");
    receive(p, receipt("100", "99", "98"), 201);
    stock(f, "98", "0", "98");
    String reservation = reserve(f, "80");
    stock(f, "98", "80", "18");
    action(
        "/api/v1/stock-reservations/" + reservation + "/issues",
        Map.of(
            "movementId",
            UUID.randomUUID().toString(),
            "quantity",
            30,
            "reference",
            "Truck gate slip"),
        201);
    stock(f, "68", "50", "18");
    action(
        "/api/v1/stock-reservations/" + reservation + "/release",
        Map.of("reason", "R".repeat(1000)),
        200);
    stock(f, "68", "0", "68");
    purchase(f, "DIRECT", "70");
    mvc.perform(get("/api/v1/order-items/" + f.line() + "/fulfillment"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allocatedQuantity").value(100))
        .andExpect(jsonPath("$.yardIssuedQuantity").value(30))
        .andExpect(jsonPath("$.unallocatedQuantity").value(0));
    mvc.perform(get("/api/v1/orders/" + f.order()))
        .andExpect(jsonPath("$.billableQuantity").value(100))
        .andExpect(jsonPath("$.subtotal").value(120000));
    assertThat(
            db.queryForObject(
                "select count(*) from stock_movements where yard_id=?",
                Integer.class,
                UUID.fromString(f.yard())))
        .isEqualTo(2);
  }

  @Test
  void directReceiptsDoNotIncreaseYardStockAndShortfallsCanBeReplaced() throws Exception {
    var f = fixture();
    String p = purchase(f, "DIRECT", "100");
    receive(p, receipt("100", "99", "98"), 201);
    stock(f, "0", "0", "0");
    action(
        "/api/v1/purchases/" + p + "/close",
        Map.of("reason", "Delivery complete; shortfall to replacement PO"),
        200);
    purchase(f, "DIRECT", "2");
    action("/api/v1/purchases", purchaseBody(f, "DIRECT", "0.001"), 409);
    assertThat(
            db.queryForObject(
                "select count(*) from stock_movements where yard_id=?",
                Integer.class,
                UUID.fromString(f.yard())))
        .isZero();
  }

  @Test
  void receiptReplayIsExactAndDoesNotDuplicateStockOrAudit() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "10");
    var r = receipt("10", "10", "10");
    receive(p, r, 201);
    receive(p, r, 200);
    stock(f, "10", "0", "10");
    r.put("reference", "Changed reference");
    receive(p, r, 409);
    stock(f, "10", "0", "10");
    assertThat(
            db.queryForObject(
                "select count(*) from purchase_receipts where purchase_id=?",
                Integer.class,
                UUID.fromString(p)))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "select count(*) from audit_events where target_id=? and"
                    + " action='PURCHASE_RECEIVED'",
                Integer.class,
                UUID.fromString(p)))
        .isEqualTo(1);
  }

  @Test
  void issueReplayCannotConsumeStockTwiceOrChangeDetails() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "20");
    receive(p, receipt("20", "20", "20"), 201);
    String r = reserve(f, "20");
    var issue =
        new HashMap<String, Object>(
            Map.of(
                "movementId", UUID.randomUUID().toString(), "quantity", 20, "reference", "Slip"));
    action("/api/v1/stock-reservations/" + r + "/issues", issue, 201);
    action("/api/v1/stock-reservations/" + r + "/issues", issue, 200);
    stock(f, "0", "0", "0");
    issue.put("quantity", 1);
    action("/api/v1/stock-reservations/" + r + "/issues", issue, 409);
    action("/api/v1/stock-reservations/" + r + "/release", Map.of("reason", "Already issued"), 409);
  }

  @Test
  void overReceiptAndInvalidAcceptedWeightsRollbackCompletely() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "10");
    receive(p, receipt("11", "11", "11"), 409);
    receive(p, receipt("10", "9", "10"), 400);
    var r = receipt("10", "9", "9");
    r.remove("discrepancyReason");
    receive(p, r, 400);
    stock(f, "0", "0", "0");
    assertThat(
            db.queryForObject(
                "select count(*) from purchase_receipts where purchase_id=?",
                Integer.class,
                UUID.fromString(p)))
        .isZero();
  }

  @Test
  void reservationsCannotOverdrawStockOrCombineWithExcessDirectPurchases() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "120");
    receive(p, receipt("120", "120", "120"), 201);
    reserve(f, "60");
    action("/api/v1/purchases", purchaseBody(f, "DIRECT", "41"), 409);
    purchase(f, "DIRECT", "40");
    action(
        "/api/v1/stock-reservations",
        Map.of("yardId", f.yard(), "orderItemId", f.line(), "quantity", 1),
        409);
    stock(f, "120", "60", "60");
  }

  @Test
  void insufficientStockAndExcessIssueLeaveBalancesUntouched() throws Exception {
    var f = fixture();
    action(
        "/api/v1/stock-reservations",
        Map.of("yardId", f.yard(), "orderItemId", f.line(), "quantity", 1),
        409);
    String p = purchase(f, "YARD", "10");
    receive(p, receipt("10", "10", "10"), 201);
    String r = reserve(f, "10");
    action(
        "/api/v1/stock-reservations/" + r + "/issues",
        Map.of("movementId", UUID.randomUUID().toString(), "quantity", 11, "reference", "Too much"),
        409);
    stock(f, "10", "10", "0");
  }

  @Test
  void closingWithoutReceiptsCancelsAndReleasesDirectCommitment() throws Exception {
    var f = fixture();
    String p = purchase(f, "DIRECT", "100");
    action("/api/v1/purchases/" + p + "/close", Map.of("reason", "R".repeat(1000)), 200);
    assertThat(
            db.queryForObject(
                "select close_reason from purchase_orders where id=?",
                String.class,
                UUID.fromString(p)))
        .hasSize(1000);
    receive(p, receipt("10", "10", "10"), 409);
    purchase(f, "DIRECT", "100");
    action("/api/v1/purchases/" + p + "/close", Map.of("reason", "Retry"), 409);
  }

  @Test
  void sourceOwnershipMaterialAndDestinationAreValidated() throws Exception {
    var f = fixture();
    var body = purchaseBody(f, "YARD", "10");
    body.put("materialId", f.sales().second());
    action("/api/v1/purchases", body, 400);
    body = purchaseBody(f, "YARD", "10");
    body.put("orderItemId", f.line());
    action("/api/v1/purchases", body, 400);
    String other =
        id(
            create(
                "/api/v1/suppliers/" + f.supplier() + "/plants",
                Map.of("code", helpers.code(), "name", "Unlinked plant", "address", "Road")));
    body = purchaseBody(f, "YARD", "10");
    body.put("plantId", other);
    action("/api/v1/purchases", body, 400);
    body = purchaseBody(f, "DIRECT", "10");
    body.put("materialId", f.sales().second());
    mvc.perform(
            put("/api/v1/suppliers/"
                    + f.supplier()
                    + "/plants/"
                    + f.plant()
                    + "/materials/"
                    + f.sales().second())
                .with(csrf()))
        .andExpect(status().isOk());
    action("/api/v1/purchases", body, 400);
  }

  @Test
  void branchOwnershipAndInactiveYardsPreventNewStockActions() throws Exception {
    var f = fixture();
    var other = helpers.fixture();
    String yard =
        id(
            create(
                "/api/v1/yards",
                Map.of(
                    "branchId",
                    other.branch(),
                    "code",
                    helpers.code(),
                    "name",
                    "Other branch",
                    "address",
                    "Road")));
    action(
        "/api/v1/stock-reservations",
        Map.of("yardId", yard, "orderItemId", f.line(), "quantity", 1),
        400);
    String p = purchase(f, "YARD", "10");
    mvc.perform(
            patch("/api/v1/yards/" + f.yard() + "/status")
                .with(csrf())
                .contentType("application/json")
                .content("{\"active\":false}"))
        .andExpect(status().isOk());
    receive(p, receipt("10", "10", "10"), 409);
    action("/api/v1/purchases", purchaseBody(f, "YARD", "10"), 409);
    stock(f, "0", "0", "0");
  }

  @Test
  void datesPrecisionAndPriceRoundingAreExplicit() throws Exception {
    var f = fixture();
    var b = purchaseBody(f, "YARD", "0.001");
    b.put("rate", 5);
    var p = create("/api/v1/purchases", b);
    assertThat(p.read("$.subtotal", Double.class)).isEqualTo(0.01d);
    b.put("quantity", new BigDecimal("0.0001"));
    action("/api/v1/purchases", b, 400);
    b.put("quantity", 1);
    b.put("rate", new BigDecimal("1.001"));
    action("/api/v1/purchases", b, 400);
    b.put("rate", 1);
    b.put("expectedDate", helpers.today().minusDays(1).toString());
    action("/api/v1/purchases", b, 400);
    var r = receipt("0.001", "0.001", "0.001");
    r.put("receivedDate", helpers.today().plusDays(1).toString());
    receive(id(p), r, 400);
  }

  @Test
  void accountsCanReadButNotWriteAndExternalRolesAndMissingCsrfAreDenied() throws Exception {
    var f = fixture();
    mvc.perform(get("/api/v1/purchases").with(user("accounts").roles("ACCOUNTS")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/purchases")
                .with(csrf())
                .with(user("accounts").roles("ACCOUNTS"))
                .contentType("application/json")
                .content(json.writeValueAsString(purchaseBody(f, "YARD", "1"))))
        .andExpect(status().isForbidden());
    for (String role : List.of("CUSTOMER", "SUPPLIER", "DRIVER"))
      mvc.perform(get("/api/v1/yards").with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/yards").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }

  int concurrentPost(String path, Object body) throws Exception {
    return mvc.perform(
            post(path)
                .with(user("inventory-worker").roles("OPERATIONS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void concurrentReservationsCannotOverAllocateOrder() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "150");
    receive(p, receipt("150", "150", "150"), 201);
    var body = Map.of("yardId", f.yard(), "orderItemId", f.line(), "quantity", 60);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<Integer> task =
          () -> {
            gate.await();
            return concurrentPost("/api/v1/stock-reservations", body);
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(45, TimeUnit.SECONDS), b.get(45, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    stock(f, "150", "60", "90");
  }

  @Test
  void concurrentReceiptRetriesCreateExactlyOneMovement() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "10");
    var r = receipt("10", "10", "10");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<Integer> task =
          () -> {
            gate.await();
            return concurrentPost("/api/v1/purchases/" + p + "/receipts", r);
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(45, TimeUnit.SECONDS), b.get(45, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 200);
    }
    stock(f, "10", "0", "10");
  }

  @Test
  void differentOrdersCannotReserveTheSameAvailableStockConcurrently() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "100");
    receive(p, receipt("100", "100", "100"), 201);
    var other =
        create(
            "/api/v1/orders",
            Map.of(
                "customerId",
                f.sales().customer(),
                "deliverySiteId",
                f.sales().site(),
                "materialId",
                f.sales().first(),
                "businessType",
                "MATERIAL",
                "orderedQuantity",
                100,
                "unitRate",
                1000));
    String otherLine = other.read("$.items[0].id", String.class);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          executor.submit(
              () -> {
                gate.await();
                return concurrentPost(
                    "/api/v1/stock-reservations",
                    Map.of("yardId", f.yard(), "orderItemId", f.line(), "quantity", 60));
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return concurrentPost(
                    "/api/v1/stock-reservations",
                    Map.of("yardId", f.yard(), "orderItemId", otherLine, "quantity", 60));
              });
      gate.countDown();
      assertThat(List.of(a.get(45, TimeUnit.SECONDS), b.get(45, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    stock(f, "100", "60", "40");
  }

  @Test
  void directPurchaseRacingYardReservationCannotDoubleAllocateOrder() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "100");
    receive(p, receipt("100", "100", "100"), 201);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          executor.submit(
              () -> {
                gate.await();
                return concurrentPost(
                    "/api/v1/stock-reservations",
                    Map.of("yardId", f.yard(), "orderItemId", f.line(), "quantity", 80));
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return concurrentPost("/api/v1/purchases", purchaseBody(f, "DIRECT", "80"));
              });
      gate.countDown();
      assertThat(List.of(a.get(45, TimeUnit.SECONDS), b.get(45, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    assertThat(
            db.queryForObject(
                    "select coalesce(sum(quantity),0) from purchase_orders where order_item_id=?",
                    BigDecimal.class,
                    UUID.fromString(f.line()))
                .add(
                    db.queryForObject(
                        "select coalesce(sum(quantity),0) from stock_reservations where"
                            + " order_item_id=?",
                        BigDecimal.class,
                        UUID.fromString(f.line()))))
        .isEqualByComparingTo("80");
  }

  @Test
  void rejectedReceiptCreatesNoUsableStockAndCanBeReplacedDirectly() throws Exception {
    var f = fixture();
    String p = purchase(f, "DIRECT", "100");
    receive(p, receipt("100", "100", "0"), 201);
    purchase(f, "DIRECT", "100");
    stock(f, "0", "0", "0");
    String yardPurchase = purchase(f, "YARD", "10");
    receive(yardPurchase, receipt("10", "10", "0"), 201);
    stock(f, "0", "0", "0");
    assertThat(
            db.queryForObject(
                "select count(*) from stock_movements where yard_id=?",
                Integer.class,
                UUID.fromString(f.yard())))
        .isZero();
  }

  @Test
  void inactiveSupplierBlocksNewPurchasesButExistingReceiptRemainsRecordable() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "10");
    mvc.perform(
            patch("/api/v1/suppliers/" + f.supplier() + "/status")
                .with(csrf())
                .contentType("application/json")
                .content("{\"active\":false}"))
        .andExpect(status().isOk());
    action("/api/v1/purchases", purchaseBody(f, "YARD", "1"), 400);
    receive(p, receipt("10", "10", "10"), 201);
    stock(f, "10", "0", "10");
    mvc.perform(get("/api/v1/purchases/" + p))
        .andExpect(jsonPath("$.supplier_name").value("Crusher"));
  }

  @Test
  void databaseRejectsMissingWeightDiscrepancyReasons() throws Exception {
    var f = fixture();
    String p = purchase(f, "YARD", "10");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                db.update(
                    "insert into"
                        + " purchase_receipts(id,purchase_id,received_date,loaded_quantity,received_quantity,accepted_quantity,reference,recorded_by)"
                        + " values(?,?,current_date,10,9,8,'slip','test')",
                    UUID.randomUUID(),
                    UUID.fromString(p)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    stock(f, "0", "0", "0");
  }
}
