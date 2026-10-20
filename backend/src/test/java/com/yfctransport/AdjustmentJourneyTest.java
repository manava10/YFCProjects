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
@WithMockUser(username = "stock-submitter", roles = "SUPER_ADMIN")
class AdjustmentJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  InventoryJourneyTest h;
  InventoryJourneyTest.Fixture f;

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

  Map<String, Object> request(String kind, String quantity, String expected) {
    return new HashMap<>(
        Map.of(
            "adjustmentId",
            uid(),
            "kind",
            kind,
            "yardId",
            f.yard(),
            "materialId",
            f.sales().first(),
            "quantity",
            new BigDecimal(quantity),
            "expectedOnHand",
            new BigDecimal(expected),
            "occurredOn",
            h.helpers.today().toString(),
            "reason",
            "Independent physical count discrepancy",
            "evidenceReference",
            "Signed stock count sheet"));
  }

  String submit(Map<String, Object> body) throws Exception {
    h.action("/api/v1/stock-adjustments", body, 201);
    return body.get("adjustmentId").toString();
  }

  Map<String, Object> decision() {
    return Map.of("eventId", uid(), "reason", "Physical record independently checked");
  }

  int review(String id, String action, Map<String, Object> body, String user, String role)
      throws Exception {
    return mvc.perform(
            post("/api/v1/stock-adjustments/" + id + "/" + action)
                .with(user(user).roles(role))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  void approve(String id) throws Exception {
    assertThat(review(id, "approve", decision(), "reviewer", "ACCOUNTS")).isEqualTo(200);
  }

  BigDecimal stock(String yard, String material) {
    return db.queryForObject(
        "select coalesce(sum(on_hand),0) from stock_balances where yard_id=? and material_id=?",
        BigDecimal.class,
        UUID.fromString(yard),
        UUID.fromString(material));
  }

  @Test
  void manualIncreaseDecreaseAuditedMovementsAndRetry() throws Exception {
    var input = request("INCREASE", "10", "0");
    String id = submit(input);
    assertThat(stock(f.yard(), f.sales().first())).isZero();
    var d = decision();
    assertThat(review(id, "approve", d, "reviewer", "ACCOUNTS")).isEqualTo(200);
    assertThat(review(id, "approve", d, "reviewer", "ACCOUNTS")).isEqualTo(200);
    h.action("/api/v1/stock-adjustments", input, 200);
    assertThat(stock(f.yard(), f.sales().first())).isEqualByComparingTo("10");
    approve(submit(request("DECREASE", "3", "10")));
    assertThat(stock(f.yard(), f.sales().first())).isEqualByComparingTo("7");
    mvc.perform(get("/api/v1/yards/" + f.yard() + "/stock/" + f.sales().first() + "/movements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    assertThat(
            db.queryForObject(
                "select count(*) from stock_adjustment_movements where adjustment_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "select count(*) from audit_events where target_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(2);
  }

  @Test
  void selfReviewOpsAndExternalPermissions() throws Exception {
    String id = submit(request("INCREASE", "2", "0"));
    assertThat(review(id, "approve", decision(), "stock-submitter", "SUPER_ADMIN")).isEqualTo(409);
    assertThat(review(id, "approve", decision(), "ops", "OPERATIONS")).isEqualTo(403);
    for (String role : List.of("DRIVER", "CUSTOMER", "SUPPLIER")) {
      mvc.perform(get("/api/v1/stock-adjustments/" + id).with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/api/v1/stock-adjustments")
                .with(csrf())
                .with(user("accounts").roles("ACCOUNTS"))
                .contentType("application/json")
                .content(json.writeValueAsString(request("INCREASE", "1", "0"))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/stock-adjustments")
                .contentType("application/json")
                .content(json.writeValueAsString(request("INCREASE", "1", "0"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void staleCountAndReservedStockCannotBeConsumed() throws Exception {
    String stale = submit(request("INCREASE", "1", "0"));
    approve(submit(request("INCREASE", "10", "0")));
    assertThat(review(stale, "approve", decision(), "reviewer", "ACCOUNTS")).isEqualTo(409);
    h.reserve(f, "9");
    String decrease = submit(request("DECREASE", "2", "10"));
    assertThat(review(decrease, "approve", decision(), "reviewer", "ACCOUNTS")).isEqualTo(409);
    assertThat(stock(f.yard(), f.sales().first())).isEqualByComparingTo("10");
    assertThat(
            db.queryForObject(
                "select status from stock_adjustments where id=?",
                String.class,
                UUID.fromString(decrease)))
        .isEqualTo("PENDING");
    approve(submit(request("DECREASE", "1", "10")));
  }

  @Test
  void rejectionIsImmutableAndIdConflictsAreRejected() throws Exception {
    var input = request("INCREASE", "10", "0");
    String id = submit(input);
    var d = decision();
    assertThat(review(id, "reject", d, "reviewer", "ACCOUNTS")).isEqualTo(200);
    assertThat(review(id, "reject", d, "reviewer", "ACCOUNTS")).isEqualTo(200);
    assertThat(review(id, "approve", decision(), "reviewer", "ACCOUNTS")).isEqualTo(409);
    input.put("quantity", new BigDecimal("11"));
    h.action("/api/v1/stock-adjustments", input, 409);
    String other = submit(request("INCREASE", "2", "0"));
    assertThat(review(other, "approve", d, "reviewer", "ACCOUNTS")).isEqualTo(409);
    assertThat(stock(f.yard(), f.sales().first())).isZero();
  }

  @Test
  void invalidDateQuantityAndEvidenceRejected() throws Exception {
    var r = request("INCREASE", "1", "0");
    r.put("occurredOn", h.helpers.today().plusDays(1).toString());
    h.action("/api/v1/stock-adjustments", r, 400);
    r = request("INCREASE", "0", "0");
    h.action("/api/v1/stock-adjustments", r, 400);
    r = request("INCREASE", "1.0001", "0");
    h.action("/api/v1/stock-adjustments", r, 400);
    r = request("INCREASE", "1", "0");
    r.put("evidenceReference", " ");
    h.action("/api/v1/stock-adjustments", r, 400);
  }

  record Delivered(TripJourneyTest helper, TripJourneyTest.Fixture fixture, String trip) {}

  Delivered delivered(String source) throws Exception {
    var t = new TripJourneyTest();
    t.mvc = mvc;
    t.json = json;
    t.db = db;
    t.setup();
    var fixture = t.fixture(source);
    String trip = t.allocate(fixture);
    t.finished(trip);
    return new Delivered(t, fixture, trip);
  }

  Map<String, Object> returned(Delivered d, String quantity) {
    return new HashMap<>(
        Map.of(
            "returnId",
            uid(),
            "yardId",
            d.fixture().inventory().yard(),
            "quantity",
            new BigDecimal(quantity),
            "receivedOn",
            h.helpers.today().toString(),
            "resalable",
            true,
            "reason",
            "Customer returned unused accepted material",
            "evidenceReference",
            "Signed return weighbridge slip"));
  }

  String returnPath(Delivered d) {
    return "/api/v1/trips/" + d.trip() + "/returns";
  }

  @Test
  void yardAndDirectReturnsIncreaseStockWithoutChangingDelivery() throws Exception {
    for (String source : List.of("YARD", "DIRECT")) {
      var d = delivered(source);
      var r = returned(d, "2");
      String yard = d.fixture().inventory().yard(),
          material = d.fixture().inventory().sales().first();
      BigDecimal before = stock(yard, material);
      h.action(returnPath(d), r, 201);
      String id = r.get("returnId").toString();
      approve(id);
      h.action(returnPath(d), r, 200);
      assertThat(stock(yard, material)).isEqualByComparingTo(before.add(new BigDecimal("2")));
      assertThat(
              db.queryForObject(
                  "select accepted_quantity from trips where id=?",
                  BigDecimal.class,
                  UUID.fromString(d.trip())))
          .isEqualByComparingTo("28");
      assertThat(
              db.queryForObject(
                  "select ordered_quantity from sales_order_items where id=?",
                  BigDecimal.class,
                  UUID.fromString(d.fixture().item())))
          .isEqualByComparingTo("100");
    }
  }

  @Test
  void pendingReturnsHoldCapacityAndRejectionReleasesIt() throws Exception {
    var d = delivered("DIRECT");
    var a = returned(d, "20");
    h.action(returnPath(d), a, 201);
    h.action(returnPath(d), returned(d, "9"), 409);
    assertThat(review(a.get("returnId").toString(), "reject", decision(), "reviewer", "ACCOUNTS"))
        .isEqualTo(200);
    var b = returned(d, "28");
    h.action(returnPath(d), b, 201);
    approve(b.get("returnId").toString());
    h.action(returnPath(d), returned(d, "0.001"), 409);
  }

  @Test
  void nonResalableReturnDoesNotEnterUsableStock() throws Exception {
    var d = delivered("DIRECT");
    BigDecimal before = stock(d.fixture().inventory().yard(), d.fixture().inventory().sales().first());
    var r = returned(d, "2");
    r.put("resalable", false);
    h.action(returnPath(d), r, 201);
    approve(r.get("returnId").toString());
    assertThat(stock(d.fixture().inventory().yard(), d.fixture().inventory().sales().first()))
        .isEqualByComparingTo(before);
  }

  @Test
  void returnEligibilityAndBranchOwnership() throws Exception {
    var d = delivered("DIRECT");
    var r = returned(d, "1");
    r.put("yardId", f.yard());
    h.action(returnPath(d), r, 409);
    r = returned(d, "1");
    r.put("receivedOn", "2000-01-01");
    h.action(returnPath(d), r, 400);
    r = returned(d, "1");
    r.put("resalable", false);
    h.action(returnPath(d), r, 201);
    var t = d.helper();
    var next = t.fixture("DIRECT");
    String allocated = t.allocate(next);
    h.action("/api/v1/trips/" + allocated + "/returns", returned(d, "1"), 409);
    var transport = delivered("TRANSPORT");
    h.action(returnPath(transport), returned(transport, "1"), 409);
  }

  @Test
  void concurrentCountsHaveOneWinner() throws Exception {
    String a = submit(request("INCREASE", "2", "0")), b = submit(request("INCREASE", "3", "0"));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> {
                gate.await();
                return review(a, "approve", decision(), "reviewer", "ACCOUNTS");
              });
      var two =
          pool.submit(
              () -> {
                gate.await();
                return review(b, "approve", decision(), "reviewer", "ACCOUNTS");
              });
      gate.countDown();
      assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  void concurrentReturnSubmissionsCannotExceedAcceptedQuantity() throws Exception {
    var d = delivered("DIRECT");
    var gate = new CountDownLatch(1);
    var a = returned(d, "20");
    var b = returned(d, "20");
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Integer> first =
          () -> {
            gate.await();
            return submitReturnAs(returnPath(d), a);
          };
      Callable<Integer> second =
          () -> {
            gate.await();
            return submitReturnAs(returnPath(d), b);
          };
      var one = pool.submit(first);
      var two = pool.submit(second);
      gate.countDown();
      assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  int submitReturnAs(String path, Object r) throws Exception {
    return mvc.perform(
            post(path)
                .with(user("ops").roles("OPERATIONS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(r)))
        .andReturn()
        .getResponse()
        .getStatus();
  }
}
