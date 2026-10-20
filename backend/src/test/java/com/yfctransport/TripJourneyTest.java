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
@WithMockUser(username = "trip-admin", roles = "SUPER_ADMIN")
class TripJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  InventoryJourneyTest h;

  @BeforeEach
  void setup() {
    h = new InventoryJourneyTest();
    h.mvc = mvc;
    h.json = json;
    h.db = db;
    h.setup();
  }

  record Fixture(
      InventoryJourneyTest.Fixture inventory,
      String truck,
      String driver,
      String item,
      String source,
      String sourceId) {}

  String id(DocumentContext d) {
    return h.id(d);
  }

  String uid() {
    return UUID.randomUUID().toString();
  }

  DocumentContext create(String path, Object body) throws Exception {
    return h.create(path, body);
  }

  DocumentContext action(String path, Object body, int status) throws Exception {
    return h.action(path, body, status);
  }

  void putBody(String path, Object body, int expected) throws Exception {
    mvc.perform(
            put(path)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().is(expected));
  }

  Map<String, Object> compliance(Fixture f) {
    return new HashMap<>(
        Map.of(
            "branchId",
            f.inventory().sales().branch(),
            "ownerName",
            "YFC",
            "truckType",
            "Tipper",
            "rcNumber",
            "RC-123",
            "insuranceNumber",
            "POL-123",
            "insuranceExpiry",
            h.helpers.today().plusYears(1).toString(),
            "fitnessExpiry",
            h.helpers.today().plusYears(1).toString(),
            "permitExpiry",
            h.helpers.today().plusYears(1).toString(),
            "pollutionExpiry",
            h.helpers.today().plusYears(1).toString(),
            "status",
            "ACTIVE"));
  }

  Map<String, Object> driverBody(String branch) {
    return new HashMap<>(
        Map.of(
            "branchId",
            branch,
            "name",
            "Driver A",
            "mobile",
            "9876543210",
            "licenseNumber",
            h.helpers.code(),
            "licenseExpiry",
            h.helpers.today().plusYears(1).toString(),
            "emergencyContact",
            "Family",
            "emergencyMobile",
            "9876543211",
            "active",
            true));
  }

  String truck() throws Exception {
    return id(
        create(
            "/api/v1/trucks",
            Map.of(
                "registration",
                h.helpers.code().toUpperCase(Locale.ROOT),
                "capacityTonnes",
                35,
                "tankCapacityLitres",
                400,
                "mileageKmPerLitre",
                3)));
  }

  Fixture fixture(String source) throws Exception {
    var f = h.fixture();
    String truck = truck();
    String driver = id(create("/api/v1/drivers", driverBody(f.sales().branch())));
    String item = f.line(), sourceId = null;
    if (source.equals("YARD")) {
      String p = h.purchase(f, "YARD", "100");
      h.receive(p, h.receipt("100", "100", "100"), 201);
      sourceId = h.reserve(f, "100");
    }
    if (source.equals("DIRECT")) sourceId = h.purchase(f, "DIRECT", "100");
    if (source.equals("TRANSPORT")) {
      var order =
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
                  "TRANSPORT",
                  "orderedQuantity",
                  100,
                  "unitRate",
                  200));
      item = order.read("$.items[0].id", String.class);
    }
    var result = new Fixture(f, truck, driver, item, source, sourceId);
    putBody("/api/v1/trucks/" + truck + "/compliance", compliance(result), 200);
    return result;
  }

  Map<String, Object> allocation(Fixture f) {
    var body =
        new HashMap<String, Object>(
            Map.of(
                "tripId",
                uid(),
                "orderItemId",
                f.item(),
                "truckId",
                f.truck(),
                "driverId",
                f.driver(),
                "source",
                f.source(),
                "quantity",
                30,
                "plannedDate",
                h.helpers.today().toString(),
                "distanceKm",
                160));
    if (f.source().equals("YARD")) body.put("reservationId", f.sourceId());
    if (f.source().equals("DIRECT")) body.put("purchaseId", f.sourceId());
    return body;
  }

  String allocate(Fixture f) throws Exception {
    return id(create("/api/v1/trips", allocation(f)));
  }

  Map<String, Object> event() {
    return Map.of("eventId", uid());
  }

  void step(String trip, String action) throws Exception {
    action("/api/v1/trips/" + trip + "/" + action, event(), 200);
  }

  Map<String, Object> load(String net) {
    return Map.of(
        "eventId",
        uid(),
        "gross",
        new BigDecimal(net).add(new BigDecimal("12")),
        "tare",
        12,
        "slipReference",
        "Plant scale slip");
  }

  Map<String, Object> delivery(String received, String accepted) {
    return new HashMap<>(
        Map.of(
            "eventId",
            uid(),
            "gross",
            new BigDecimal(received).add(new BigDecimal("12")),
            "tare",
            12,
            "acceptedQuantity",
            new BigDecimal(accepted),
            "slipReference",
            "Customer scale slip",
            "receiverName",
            "Site engineer",
            "proofReference",
            "POD-REFERENCE",
            "discrepancyReason",
            "Weighbridge/quality difference recorded"));
  }

  void loaded(String t) throws Exception {
    step(t, "start-loading");
    action("/api/v1/trips/" + t + "/load", load("30"), 200);
  }

  void dispatched(String t) throws Exception {
    loaded(t);
    step(t, "dispatch");
  }

  void atDelivery(String t) throws Exception {
    dispatched(t);
    step(t, "arrive");
    step(t, "start-unloading");
  }

  void finished(String t) throws Exception {
    atDelivery(t);
    action("/api/v1/trips/" + t + "/deliver", delivery("29", "28"), 200);
  }

  @Test
  void yardTripIssuesStockOnceAtDispatchAndUpdatesOrderWeightsWithoutChangingBilling()
      throws Exception {
    var f = fixture("YARD");
    String t = allocate(f);
    h.stock(f.inventory(), "100", "100", "0");
    loaded(t);
    h.stock(f.inventory(), "100", "100", "0");
    var dispatchEvent = event();
    action("/api/v1/trips/" + t + "/dispatch", dispatchEvent, 200);
    action("/api/v1/trips/" + t + "/dispatch", dispatchEvent, 200);
    h.stock(f.inventory(), "70", "70", "0");
    mvc.perform(get("/api/v1/orders/" + f.inventory().order()))
        .andExpect(jsonPath("$.loadedQuantity").value(30))
        .andExpect(jsonPath("$.receivedQuantity").value(0));
    step(t, "arrive");
    step(t, "start-unloading");
    action("/api/v1/trips/" + t + "/deliver", delivery("29", "28"), 200);
    h.stock(f.inventory(), "70", "70", "0");
    mvc.perform(get("/api/v1/orders/" + f.inventory().order()))
        .andExpect(jsonPath("$.receivedQuantity").value(29))
        .andExpect(jsonPath("$.billableQuantity").value(100))
        .andExpect(jsonPath("$.subtotal").value(120000));
    assertThat(
            db.queryForObject(
                "select count(*) from fleet_assignments where trip_id=?",
                Integer.class,
                UUID.fromString(t)))
        .isZero();
    assertThat(
            db.queryForObject(
                "select count(*) from stock_movements where reservation_id=?",
                Integer.class,
                UUID.fromString(f.sourceId())))
        .isEqualTo(1);
  }

  @Test
  void directTripCreatesOnePurchaseReceiptAndNeverAddsYardStock() throws Exception {
    var f = fixture("DIRECT");
    String t = allocate(f);
    action(
        "/api/v1/purchases/" + f.sourceId() + "/close",
        Map.of("reason", "Cannot close active allocation"),
        409);
    h.receive(f.sourceId(), h.receipt("1", "1", "1"), 409);
    finished(t);
    h.stock(f.inventory(), "0", "0", "0");
    assertThat(
            db.queryForObject(
                "select received_quantity from purchase_orders where id=?",
                BigDecimal.class,
                UUID.fromString(f.sourceId())))
        .isEqualByComparingTo("29");
    assertThat(
            db.queryForObject(
                "select accepted_quantity from purchase_orders where id=?",
                BigDecimal.class,
                UUID.fromString(f.sourceId())))
        .isEqualByComparingTo("28");
    assertThat(
            db.queryForObject(
                "select count(*) from purchase_receipts where id=?",
                Integer.class,
                UUID.fromString(t)))
        .isEqualTo(1);
  }

  @Test
  void transportOnlyTripHasNoYfcMaterialStockEffectAndFuelIsEstimateOnly() throws Exception {
    var f = fixture("TRANSPORT");
    var allocation = create("/api/v1/trips", allocation(f));
    String t = id(allocation);
    assertThat(allocation.read("$.estimated_fuel_litres", Double.class)).isEqualTo(53.334d);
    finished(t);
    assertThat(
            db.queryForObject(
                "select count(*) from trip_supply_claims where trip_id=?",
                Integer.class,
                UUID.fromString(t)))
        .isZero();
    h.stock(f.inventory(), "0", "0", "0");
  }

  @Test
  void cancellationReleasesCrewAndTripHoldButPreservesUnderlyingReservation() throws Exception {
    var f = fixture("YARD");
    String t = allocate(f);
    action(
        "/api/v1/stock-reservations/" + f.sourceId() + "/release",
        Map.of("reason", "Trip is allocated"),
        409);
    action(
        "/api/v1/stock-reservations/" + f.sourceId() + "/issues",
        Map.of("movementId", uid(), "quantity", 71, "reference", "Cannot use claimed stock"),
        409);
    action(
        "/api/v1/trips/" + t + "/cancel",
        Map.of("eventId", uid(), "reason", "R".repeat(1000)),
        200);
    h.stock(f.inventory(), "100", "100", "0");
    String next = allocate(f);
    assertThat(next).isNotEqualTo(t);
    action("/api/v1/trips/" + next + "/cancel", Map.of("eventId", uid(), "reason", "Done"), 200);
    action(
        "/api/v1/stock-reservations/" + f.sourceId() + "/release",
        Map.of("reason", "Release balance"),
        200);
    h.stock(f.inventory(), "100", "0", "100");
  }

  @Test
  void dispatchedTripCannotBeCancelledAndDoesNotReturnStockImplicitly() throws Exception {
    var f = fixture("YARD");
    String t = allocate(f);
    dispatched(t);
    action(
        "/api/v1/trips/" + t + "/cancel",
        Map.of("eventId", uid(), "reason", "Vehicle breakdown"),
        409);
    h.stock(f.inventory(), "70", "70", "0");
    assertThat(
            db.queryForObject(
                "select count(*) from fleet_assignments where trip_id=?",
                Integer.class,
                UUID.fromString(t)))
        .isEqualTo(1);
  }

  @Test
  void invalidTransitionsAreRejectedWithoutEventsOrStockChanges() throws Exception {
    var f = fixture("YARD");
    String t = allocate(f);
    action("/api/v1/trips/" + t + "/dispatch", event(), 409);
    action("/api/v1/trips/" + t + "/arrive", event(), 409);
    action("/api/v1/trips/" + t + "/load", load("30"), 409);
    action("/api/v1/trips/" + t + "/deliver", delivery("29", "28"), 409);
    assertThat(
            db.queryForObject(
                "select count(*) from trip_events where trip_id=?",
                Integer.class,
                UUID.fromString(t)))
        .isZero();
    h.stock(f.inventory(), "100", "100", "0");
  }

  @Test
  void oversizedAndWrongSourceAllocationsRollbackAllClaims() throws Exception {
    var f = fixture("YARD");
    var b = allocation(f);
    b.put("quantity", 36);
    action("/api/v1/trips", b, 409);
    b = allocation(f);
    b.put("source", "DIRECT");
    action("/api/v1/trips", b, 400);
    assertThat(
            db.queryForObject(
                "select count(*) from fleet_assignments where truck_id=?",
                Integer.class,
                UUID.fromString(f.truck())))
        .isZero();
    assertThat(
            db.queryForObject(
                "select fulfillment_mode from sales_order_items where id=?",
                String.class,
                UUID.fromString(f.item())))
        .isEqualTo("LEGACY");
  }

  @Test
  void expiredVehicleDocumentsAndExpiredLicensesPreventAllocation() throws Exception {
    var f = fixture("YARD");
    var c = compliance(f);
    c.put("insuranceExpiry", h.helpers.today().minusDays(1).toString());
    putBody("/api/v1/trucks/" + f.truck() + "/compliance", c, 200);
    action("/api/v1/trips", allocation(f), 409);
    putBody("/api/v1/trucks/" + f.truck() + "/compliance", compliance(f), 200);
    var d = driverBody(f.inventory().sales().branch());
    d.put("licenseExpiry", h.helpers.today().minusDays(1).toString());
    putBody("/api/v1/drivers/" + f.driver(), d, 200);
    action("/api/v1/trips", allocation(f), 409);
  }

  @Test
  void dispatchRechecksVehicleStatusAndDriverLicense() throws Exception {
    var f = fixture("YARD");
    String t = allocate(f);
    loaded(t);
    var c = compliance(f);
    c.put("status", "MAINTENANCE");
    putBody("/api/v1/trucks/" + f.truck() + "/compliance", c, 200);
    action("/api/v1/trips/" + t + "/dispatch", event(), 409);
    h.stock(f.inventory(), "100", "100", "0");
    putBody("/api/v1/trucks/" + f.truck() + "/compliance", compliance(f), 200);
    var d = driverBody(f.inventory().sales().branch());
    d.put("active", false);
    putBody("/api/v1/drivers/" + f.driver(), d, 200);
    action("/api/v1/trips/" + t + "/dispatch", event(), 409);
    d.put("active", true);
    putBody("/api/v1/drivers/" + f.driver(), d, 200);
    step(t, "dispatch");
    h.stock(f.inventory(), "70", "70", "0");
  }

  @Test
  void invalidWeighbridgeWeightsAndMissingReasonsDoNotCompleteDelivery() throws Exception {
    var f = fixture("DIRECT");
    String t = allocate(f);
    step(t, "start-loading");
    action(
        "/api/v1/trips/" + t + "/load",
        Map.of("eventId", uid(), "gross", 12, "tare", 12, "slipReference", "Zero net"),
        400);
    action("/api/v1/trips/" + t + "/load", load("31"), 409);
    action("/api/v1/trips/" + t + "/load", load("30"), 200);
    step(t, "dispatch");
    step(t, "arrive");
    step(t, "start-unloading");
    var d = delivery("29", "28");
    d.remove("discrepancyReason");
    action("/api/v1/trips/" + t + "/deliver", d, 400);
    action("/api/v1/trips/" + t + "/deliver", delivery("31", "30"), 409);
    action("/api/v1/trips/" + t + "/deliver", delivery("29", "30"), 400);
    assertThat(
            db.queryForObject(
                "select received_quantity from purchase_orders where id=?",
                BigDecimal.class,
                UUID.fromString(f.sourceId())))
        .isEqualByComparingTo("0");
  }

  @Test
  void partialLoadIssuesOnlyActualLoadAndLeavesReservationBalanceAvailable() throws Exception {
    var f = fixture("YARD");
    String t = allocate(f);
    step(t, "start-loading");
    action("/api/v1/trips/" + t + "/load", load("20"), 200);
    step(t, "dispatch");
    h.stock(f.inventory(), "80", "80", "0");
    String otherTruck = truck();
    var other =
        new Fixture(
            f.inventory(),
            otherTruck,
            id(create("/api/v1/drivers", driverBody(f.inventory().sales().branch()))),
            f.item(),
            f.source(),
            f.sourceId());
    putBody("/api/v1/trucks/" + otherTruck + "/compliance", compliance(other), 200);
    var b = allocation(other);
    b.put("quantity", 35);
    create("/api/v1/trips", b);
    action(
        "/api/v1/stock-reservations/" + f.sourceId() + "/issues",
        Map.of("movementId", uid(), "quantity", 46, "reference", "Over remaining free stock"),
        409);
  }

  @Test
  void exactRetriesDoNotRepeatDispatchStockOrDeliveryReceipt() throws Exception {
    var f = fixture("DIRECT");
    var b = allocation(f);
    String t = id(create("/api/v1/trips", b));
    action("/api/v1/trips", b, 200);
    b.put("distanceKm", 161);
    action("/api/v1/trips", b, 409);
    loaded(t);
    var e = event();
    action("/api/v1/trips/" + t + "/dispatch", e, 200);
    action("/api/v1/trips/" + t + "/dispatch", e, 200);
    step(t, "arrive");
    step(t, "start-unloading");
    var d = delivery("29", "28");
    action("/api/v1/trips/" + t + "/deliver", d, 200);
    action("/api/v1/trips/" + t + "/deliver", d, 200);
    d.put("proofReference", "Changed proof");
    action("/api/v1/trips/" + t + "/deliver", d, 409);
    assertThat(
            db.queryForObject(
                "select count(*) from dispatches where id=?", Integer.class, UUID.fromString(t)))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "select count(*) from purchase_receipts where id=?",
                Integer.class,
                UUID.fromString(t)))
        .isEqualTo(1);
  }

  @Test
  void eventIdCannotBeReusedForAnotherAction() throws Exception {
    var f = fixture("TRANSPORT");
    String t = allocate(f);
    var e = event();
    action("/api/v1/trips/" + t + "/start-loading", e, 200);
    action("/api/v1/trips/" + t + "/dispatch", e, 409);
  }

  @Test
  void legacyDispatchAndTripWorkflowCannotBeMixed() throws Exception {
    var f = fixture("YARD");
    action(
        "/api/v1/orders/" + f.inventory().order() + "/dispatches",
        Map.of("reference", h.helpers.code(), "loadedQuantity", 1, "receivedQuantity", 1),
        201);
    action("/api/v1/trips", allocation(f), 409);
    var g = fixture("YARD");
    allocate(g);
    action(
        "/api/v1/orders/" + g.inventory().order() + "/dispatches",
        Map.of("reference", h.helpers.code(), "loadedQuantity", 1, "receivedQuantity", 1),
        409);
  }

  @Test
  void futureTripsCheckFutureExpiryAndCannotStartEarly() throws Exception {
    var f = fixture("TRANSPORT");
    var b = allocation(f);
    b.put("plannedDate", h.helpers.today().plusDays(2).toString());
    String t = id(create("/api/v1/trips", b));
    action("/api/v1/trips/" + t + "/start-loading", event(), 409);
    action("/api/v1/trips/" + t + "/cancel", Map.of("eventId", uid(), "reason", "Reschedule"), 200);
    var c = compliance(f);
    c.put("permitExpiry", h.helpers.today().plusDays(1).toString());
    putBody("/api/v1/trucks/" + f.truck() + "/compliance", c, 200);
    b.put("tripId", uid());
    action("/api/v1/trips", b, 409);
  }

  @Test
  void branchChangesWhileAssignedAreRejected() throws Exception {
    var f = fixture("TRANSPORT");
    allocate(f);
    var branch = h.helpers.fixture().branch();
    var c = compliance(f);
    c.put("branchId", branch);
    putBody("/api/v1/trucks/" + f.truck() + "/compliance", c, 409);
    var d = driverBody(branch);
    putBody("/api/v1/drivers/" + f.driver(), d, 409);
  }

  @Test
  void reservationSourceMustBelongToTheSameOrderItem() throws Exception {
    var f = fixture("YARD");
    var other =
        create(
            "/api/v1/orders",
            Map.of(
                "customerId",
                f.inventory().sales().customer(),
                "deliverySiteId",
                f.inventory().sales().site(),
                "materialId",
                f.inventory().sales().first(),
                "businessType",
                "MATERIAL",
                "orderedQuantity",
                100,
                "unitRate",
                1000));
    var b = allocation(f);
    b.put("orderItemId", other.read("$.items[0].id", String.class));
    action("/api/v1/trips", b, 409);
    assertThat(
            db.queryForObject(
                "select count(*) from fleet_assignments where truck_id=?",
                Integer.class,
                UUID.fromString(f.truck())))
        .isZero();
  }

  @Test
  void accountsReadTripsButCannotChangeThemAndExternalRolesCannotReadInternalTrips()
      throws Exception {
    var f = fixture("TRANSPORT");
    mvc.perform(
            post("/api/v1/trips")
                .with(user("accounts").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(allocation(f))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/trips").with(user("accounts").roles("ACCOUNTS")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/drivers").with(user("accounts").roles("ACCOUNTS")))
        .andExpect(status().isForbidden());
    for (String role : List.of("CUSTOMER", "SUPPLIER", "DRIVER"))
      mvc.perform(get("/api/v1/trips").with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/trips")
                .contentType("application/json")
                .content(json.writeValueAsString(allocation(f))))
        .andExpect(status().isForbidden());
  }

  String anotherTransportItem(Fixture f) throws Exception {
    return create(
            "/api/v1/orders",
            Map.of(
                "customerId",
                f.inventory().sales().customer(),
                "deliverySiteId",
                f.inventory().sales().site(),
                "materialId",
                f.inventory().sales().first(),
                "businessType",
                "TRANSPORT",
                "orderedQuantity",
                100,
                "unitRate",
                200))
        .read("$.items[0].id", String.class);
  }

  int concurrentPost(String path, Object body) throws Exception {
    return mvc.perform(
            post(path)
                .with(user("trip-worker").roles("OPERATIONS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  void race(String path, Object first, Object second, int a, int b) throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var x =
          executor.submit(
              () -> {
                gate.await();
                return concurrentPost(path, first);
              });
      var y =
          executor.submit(
              () -> {
                gate.await();
                return concurrentPost(path, second);
              });
      gate.countDown();
      assertThat(List.of(x.get(45, TimeUnit.SECONDS), y.get(45, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(a, b);
    }
  }

  @Test
  void concurrentDifferentOrdersCannotAssignTheSameTruck() throws Exception {
    var f = fixture("TRANSPORT");
    var a = allocation(f);
    var b = allocation(f);
    b.put("orderItemId", anotherTransportItem(f));
    b.put("driverId", id(create("/api/v1/drivers", driverBody(f.inventory().sales().branch()))));
    race("/api/v1/trips", a, b, 201, 409);
    assertThat(
            db.queryForObject(
                "select count(*) from fleet_assignments where truck_id=?",
                Integer.class,
                UUID.fromString(f.truck())))
        .isEqualTo(1);
  }

  @Test
  void concurrentDifferentTrucksCannotAssignTheSameDriver() throws Exception {
    var f = fixture("TRANSPORT");
    String truck = truck();
    var other = new Fixture(f.inventory(), truck, f.driver(), f.item(), f.source(), null);
    putBody("/api/v1/trucks/" + truck + "/compliance", compliance(other), 200);
    var a = allocation(f);
    var b = allocation(other);
    b.put("orderItemId", anotherTransportItem(f));
    race("/api/v1/trips", a, b, 201, 409);
  }

  @Test
  void concurrentStateRetriesWriteOneEvent() throws Exception {
    var f = fixture("TRANSPORT");
    String t = allocate(f);
    var e = event();
    race("/api/v1/trips/" + t + "/start-loading", e, e, 200, 200);
    assertThat(
            db.queryForObject(
                "select count(*) from trip_events where trip_id=?",
                Integer.class,
                UUID.fromString(t)))
        .isEqualTo(1);
  }

  @Test
  void exhaustedYardAndDirectSourcesRejectTripsWithoutLeavingCrewAssigned() throws Exception {
    var f = fixture("YARD");
    action(
        "/api/v1/stock-reservations/" + f.sourceId() + "/issues",
        Map.of("movementId", uid(), "quantity", 80, "reference", "Earlier stock issue"),
        201);
    action("/api/v1/trips", allocation(f), 409);
    assertThat(
            db.queryForObject(
                "select count(*) from fleet_assignments where truck_id=?",
                Integer.class,
                UUID.fromString(f.truck())))
        .isZero();
    var g = fixture("DIRECT");
    h.receive(g.sourceId(), h.receipt("80", "80", "80"), 201);
    action("/api/v1/trips", allocation(g), 409);
    assertThat(
            db.queryForObject(
                "select count(*) from fleet_assignments where truck_id=?",
                Integer.class,
                UUID.fromString(g.truck())))
        .isZero();
  }

  @Test
  void inTransitComplianceChangesDoNotPreventRecordingActualDelivery() throws Exception {
    var f = fixture("TRANSPORT");
    String t = allocate(f);
    dispatched(t);
    var c = compliance(f);
    c.put("status", "MAINTENANCE");
    putBody("/api/v1/trucks/" + f.truck() + "/compliance", c, 200);
    var d = driverBody(f.inventory().sales().branch());
    d.put("active", false);
    putBody("/api/v1/drivers/" + f.driver(), d, 200);
    step(t, "arrive");
    step(t, "start-unloading");
    action("/api/v1/trips/" + t + "/deliver", delivery("30", "30"), 200);
    assertThat(
            db.queryForObject(
                "select count(*) from fleet_assignments where trip_id=?",
                Integer.class,
                UUID.fromString(t)))
        .isZero();
  }

  @Test
  void complianceAlertsIncludeExpiringDocumentsAndValidateWindow() throws Exception {
    var f = fixture("TRANSPORT");
    var c = compliance(f);
    c.put("fitnessExpiry", h.helpers.today().plusDays(3).toString());
    putBody("/api/v1/trucks/" + f.truck() + "/compliance", c, 200);
    var response =
        mvc.perform(
                get("/api/v1/fleet/compliance-alerts")
                    .param("branchId", f.inventory().sales().branch())
                    .param("days", "7")
                    .param("size", "100"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(response, "$[*].id");
    assertThat(ids).contains(f.truck());
    mvc.perform(get("/api/v1/fleet/compliance-alerts").param("days", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void driverComplianceAlertsIncludeExpiringLicences() throws Exception {
    var f = fixture("TRANSPORT");
    var body = driverBody(f.inventory().sales().branch());
    body.put("licenseExpiry", h.helpers.today().plusDays(2).toString());
    String driver = id(h.create("/api/v1/drivers", body));
    var response =
        mvc.perform(
                get("/api/v1/fleet/driver-compliance-alerts")
                    .param("branchId", f.inventory().sales().branch())
                    .param("days", "7")
                    .param("size", "100"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(response, "$[*].id");
    assertThat(ids).contains(driver);
  }
}
