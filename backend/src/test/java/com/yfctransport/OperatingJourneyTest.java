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
@WithMockUser(username = "submitter", roles = "SUPER_ADMIN")
class OperatingJourneyTest {
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

  record Fixture(String truck, String rate) {}

  String uid() {
    return UUID.randomUUID().toString();
  }

  String id(DocumentContext r) {
    return h.id(r);
  }

  DocumentContext create(String path, Object body) throws Exception {
    return h.create(path, body);
  }

  DocumentContext action(String path, Object body, int status) throws Exception {
    return h.action(path, body, status);
  }

  Fixture fixture() throws Exception {
    String truck =
        id(
            create(
                "/api/v1/trucks",
                Map.of(
                    "registration",
                    h.helpers.code().toUpperCase(),
                    "capacityTonnes",
                    35,
                    "tankCapacityLitres",
                    400,
                    "mileageKmPerLitre",
                    3)));
    String rate =
        id(
            create(
                "/api/v1/fuel/rates",
                Map.of(
                    "pump",
                    uid(),
                    "location",
                    "Gurgaon",
                    "effectiveDate",
                    h.helpers.today().minusYears(2).toString(),
                    "pricePerLitre",
                    new BigDecimal("91.50"))));
    return new Fixture(truck, rate);
  }

  Instant now() {
    return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  String day(Instant at) {
    return at.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().toString();
  }

  Map<String, Object> fuel(Fixture f, Instant at, String odo) {
    return new HashMap<>(
        Map.ofEntries(
            Map.entry("entryId", uid()),
            Map.entry("truckId", f.truck()),
            Map.entry("rateId", f.rate()),
            Map.entry("receiptReference", uid()),
            Map.entry("filledOn", day(at)),
            Map.entry("quantityLitres", 100),
            Map.entry("actualPricePerLitre", new BigDecimal("92.00")),
            Map.entry("odometer", new BigDecimal(odo)),
            Map.entry("filledAt", at.toString()),
            Map.entry("receiptEvidence", "Pump receipt reference"),
            Map.entry("priceVarianceReason", "Receipt rate differs from reference")));
  }

  Map<String, Object> decision(int revision) {
    return Map.of(
        "eventId", uid(), "revision", revision, "reason", "Receipt verified independently");
  }

  DocumentContext review(String path, Object body, int expected) throws Exception {
    String text =
        mvc.perform(
                post(path)
                    .with(user("reviewer").roles("ACCOUNTS"))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.parse(text.isBlank() ? "{}" : text);
  }

  String fuelPath(String id, String action) {
    return "/api/v1/fuel/entries/" + id + "/" + action;
  }

  Map<String, Object> expense(Fixture f, String trip, String category) {
    var b =
        new HashMap<String, Object>(
            Map.of(
                "expenseId",
                uid(),
                "truckId",
                f.truck(),
                "category",
                category,
                "occurredAt",
                now().toString(),
                "amount",
                new BigDecimal("123.45"),
                "description",
                "Actual operating expense",
                "receiptReference",
                uid(),
                "receiptEvidence",
                "Receipt or approved internal voucher"));
    if (trip != null) b.put("tripId", trip);
    return b;
  }

  Map<String, Object> fuelRevision(Fixture f, Map<String, Object> body, int revision) {
    var b = new HashMap<>(body);
    b.remove("entryId");
    b.remove("truckId");
    b.remove("tripId");
    b.remove("receiptReference");
    b.put("eventId", uid());
    b.put("revision", revision);
    b.put("reason", "Corrected from original receipt");
    return b;
  }

  @Test
  void approvedFuelCanBeReversedAndDropsOutOfTripCost() throws Exception {
    var f = fixture();
    String id = id(create("/api/v1/fuel/entries", fuel(f, now(), "1000.0")));
    review(fuelPath(id, "approve"), decision(1), 200);
    var reversal =
        Map.of(
            "reversalId",
            uid(),
            "reason",
            "Pump receipt belonged to a different truck",
            "evidenceReference",
            "Supervisor correction note");
    action("/api/v1/fuel/entries/" + id + "/reversals", reversal, 201);
    review("/api/v1/operating-reversals/" + reversal.get("reversalId") + "/approve", decision(1), 200);
    var viewed =
        JsonPath.parse(
            mvc.perform(get("/api/v1/fuel/entries/" + id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(viewed.read("$.reversed", Boolean.class)).isTrue();
  }

  @Test
  void independentReviewApprovesExactReceiptAmountAndPersistsIdentity() throws Exception {
    var f = fixture();
    var b = fuel(f, now(), "1000.0");
    b.put("quantityLitres", new BigDecimal("0.125"));
    String id = id(create("/api/v1/fuel/entries", b));
    var approved = review(fuelPath(id, "approve"), decision(1), 200);
    assertThat(approved.read("$.status", String.class)).isEqualTo("APPROVED");
    assertThat(approved.read("$.amount", Double.class)).isEqualTo(11.50d);
    assertThat(approved.read("$.submittedBy", String.class)).isEqualTo("submitter");
    assertThat(approved.read("$.reviewedBy", String.class)).isEqualTo("reviewer");
  }

  @Test
  void submitterCannotReviewOwnRecordAndOperationsCannotReview() throws Exception {
    var f = fixture();
    String id = id(create("/api/v1/fuel/entries", fuel(f, now(), "1000")));
    action(fuelPath(id, "approve"), decision(1), 409);
    mvc.perform(
            post(fuelPath(id, "approve"))
                .with(user("ops").roles("OPERATIONS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(decision(1))))
        .andExpect(status().isForbidden());
  }

  @Test
  void incompleteLegacyStyleSubmissionRequiresEvidenceRevisionBeforeApproval() throws Exception {
    var f = fixture();
    var body = fuel(f, now(), "1000");
    body.remove("odometer");
    body.remove("filledAt");
    body.remove("receiptEvidence");
    String id = id(create("/api/v1/fuel/entries", body));
    review(fuelPath(id, "approve"), decision(1), 400);
    var revision = fuelRevision(f, fuel(f, now(), "1000"), 1);
    action(fuelPath(id, "revisions"), revision, 200);
    review(fuelPath(id, "approve"), decision(1), 409);
    review(fuelPath(id, "approve"), decision(2), 200);
    assertThat(
            db.queryForObject(
                "select count(*) from operating_events where record_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(2);
  }

  @Test
  void tankLimitVarianceReasonAndRoundedZeroAmountsAreRejected() throws Exception {
    var f = fixture();
    var b = fuel(f, now(), "1000");
    b.put("quantityLitres", 401);
    action("/api/v1/fuel/entries", b, 409);
    b.put("quantityLitres", 100);
    b.remove("priceVarianceReason");
    action("/api/v1/fuel/entries", b, 400);
    b.put("priceVarianceReason", "Small test amount");
    b.put("quantityLitres", new BigDecimal("0.001"));
    b.put("actualPricePerLitre", new BigDecimal("0.01"));
    action("/api/v1/fuel/entries", b, 400);
  }

  @Test
  void rateCorrectionPreservesEntryReferencePriceAndActualCost() throws Exception {
    var f = fixture();
    String id = id(create("/api/v1/fuel/entries", fuel(f, now(), "1000")));
    var correction =
        new HashMap<String, Object>(
            Map.of(
                "eventId",
                uid(),
                "expectedPrice",
                new BigDecimal("91.50"),
                "newPrice",
                95,
                "reason",
                "Pump corrected today's published rate"));
    action("/api/v1/fuel/rates/" + f.rate() + "/corrections", correction, 200);
    action("/api/v1/fuel/rates/" + f.rate() + "/corrections", correction, 200);
    correction.put("eventId", uid());
    action("/api/v1/fuel/rates/" + f.rate() + "/corrections", correction, 409);
    mvc.perform(get("/api/v1/fuel/entries/" + id))
        .andExpect(jsonPath("$.referencePricePerLitre").value(91.5))
        .andExpect(jsonPath("$.actualPricePerLitre").value(92))
        .andExpect(jsonPath("$.amount").value(9200));
    mvc.perform(get("/api/v1/fuel/rates/" + f.rate()))
        .andExpect(jsonPath("$.pricePerLitre").value(95));
    assertThat(
            db.queryForObject(
                "select count(*) from diesel_rate_corrections where rate_id=?",
                Integer.class,
                UUID.fromString(f.rate())))
        .isEqualTo(1);
  }

  @Test
  void rejectedFuelCanBeRevisedWithSnapshotsButApprovedFuelCannotChange() throws Exception {
    var f = fixture();
    var b = fuel(f, now(), "1000");
    String id = id(create("/api/v1/fuel/entries", b));
    review(fuelPath(id, "reject"), decision(1), 200);
    b.put("quantityLitres", 50);
    var revision = fuelRevision(f, b, 1);
    action(fuelPath(id, "revisions"), revision, 200);
    action(fuelPath(id, "revisions"), revision, 200);
    review(fuelPath(id, "approve"), decision(2), 200);
    action(fuelPath(id, "revisions"), fuelRevision(f, b, 2), 409);
    String snapshot =
        db.queryForObject(
            "select previous_snapshot from operating_events where record_id=? and action='REVISE'",
            String.class,
            UUID.fromString(id));
    assertThat(snapshot).contains("REJECTED", "100.000");
  }

  @Test
  void exactDecisionReplayCannotDoubleReviewOrChangeReason() throws Exception {
    var f = fixture();
    String id = id(create("/api/v1/fuel/entries", fuel(f, now(), "1000")));
    var d = new HashMap<>(decision(1));
    review(fuelPath(id, "approve"), d, 200);
    review(fuelPath(id, "approve"), d, 200);
    d.put("reason", "Changed review");
    review(fuelPath(id, "approve"), d, 409);
    review(fuelPath(id, "reject"), decision(1), 409);
    assertThat(
            db.queryForObject(
                "select count(*) from operating_events where record_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void odometerChronologyChecksBothEarlierAndLaterApprovedReceipts() throws Exception {
    var f = fixture();
    Instant base = now().minusSeconds(7200);
    String first = id(create("/api/v1/fuel/entries", fuel(f, base, "1000")));
    review(fuelPath(first, "approve"), decision(1), 200);
    String later = id(create("/api/v1/fuel/entries", fuel(f, base.plusSeconds(3600), "1100")));
    review(fuelPath(later, "approve"), decision(1), 200);
    String wrong = id(create("/api/v1/fuel/entries", fuel(f, base.plusSeconds(1800), "1200")));
    review(fuelPath(wrong, "approve"), decision(1), 409);
    String good = id(create("/api/v1/fuel/entries", fuel(f, base.plusSeconds(1800), "1050")));
    review(fuelPath(good, "approve"), decision(1), 200);
    String early = id(create("/api/v1/fuel/entries", fuel(f, base.minusSeconds(600), "1100")));
    review(fuelPath(early, "approve"), decision(1), 409);
  }

  @Test
  void sameTimestampCannotHaveConflictingApprovedOdometers() throws Exception {
    var f = fixture();
    Instant at = now();
    String a = id(create("/api/v1/fuel/entries", fuel(f, at, "1000")));
    String b = id(create("/api/v1/fuel/entries", fuel(f, at, "1001")));
    review(fuelPath(a, "approve"), decision(1), 200);
    review(fuelPath(b, "approve"), decision(1), 409);
  }

  @Test
  void expenseReviewRevisionAndReadOnlyApprovedStatePreserveHistory() throws Exception {
    var f = fixture();
    var b = expense(f, null, "REPAIR");
    String id = id(create("/api/v1/expenses", b));
    review("/api/v1/expenses/" + id + "/reject", decision(1), 200);
    var revision =
        Map.of(
            "eventId",
            uid(),
            "revision",
            1,
            "reason",
            "Correct amount from receipt",
            "category",
            "REPAIR",
            "occurredAt",
            b.get("occurredAt"),
            "amount",
            new BigDecimal("200.05"),
            "description",
            "Workshop repair",
            "receiptEvidence",
            "Corrected workshop receipt");
    action("/api/v1/expenses/" + id + "/revisions", revision, 200);
    review("/api/v1/expenses/" + id + "/approve", decision(2), 200);
    action("/api/v1/expenses/" + id + "/revisions", revision, 200);
    var changed = new HashMap<String, Object>(revision);
    changed.put("eventId", uid());
    changed.put("revision", 2);
    action("/api/v1/expenses/" + id + "/revisions", changed, 409);
  }

  @Test
  void tripRequiredCategoriesAndNonYfcResponsibilitiesCannotBeSubmitted() throws Exception {
    var f = fixture();
    action("/api/v1/expenses", expense(f, null, "TOLL"), 400);
    action("/api/v1/expenses", expense(f, null, "LOADING"), 400);
    action("/api/v1/expenses", expense(f, null, "ROYALTY"), 400);
    var b = expense(f, null, "REPAIR");
    b.put("amount", new BigDecimal("1.001"));
    action("/api/v1/expenses", b, 400);
    b.put("amount", 0);
    action("/api/v1/expenses", b, 400);
  }

  record TripFixture(
      Fixture fuel, String trip, TripJourneyTest trips, TripJourneyTest.Fixture route) {}

  TripFixture tripFixture() throws Exception {
    var t = new TripJourneyTest();
    t.mvc = mvc;
    t.json = json;
    t.db = db;
    t.setup();
    var route = t.fixture("TRANSPORT");
    String trip = t.allocate(route);
    String rate =
        id(
            create(
                "/api/v1/fuel/rates",
                Map.of(
                    "pump",
                    uid(),
                    "location",
                    "Gurgaon",
                    "effectiveDate",
                    h.helpers.today().toString(),
                    "pricePerLitre",
                    new BigDecimal("91.50"))));
    return new TripFixture(new Fixture(route.truck(), rate), trip, t, route);
  }

  @Test
  void tripSummaryIncludesOnlyApprovedCostsAndSeparatesFuelEstimateFromPurchasedLitres()
      throws Exception {
    var f = tripFixture();
    var b = fuel(f.fuel(), now(), "1000");
    b.put("tripId", f.trip());
    String fuel = id(create("/api/v1/fuel/entries", b));
    String toll = id(create("/api/v1/expenses", expense(f.fuel(), f.trip(), "TOLL")));
    create("/api/v1/expenses", expense(f.fuel(), f.trip(), "PARKING"));
    review(fuelPath(fuel, "approve"), decision(1), 200);
    review("/api/v1/expenses/" + toll + "/approve", decision(1), 200);
    mvc.perform(get("/api/v1/trips/" + f.trip() + "/operating-costs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvedFuelCost").value(9200))
        .andExpect(jsonPath("$.approvedOtherOperatingCost").value(123.45))
        .andExpect(jsonPath("$.totalApprovedOperatingCost").value(9323.45))
        .andExpect(jsonPath("$.approvedFuelPurchasedLitres").value(100))
        .andExpect(jsonPath("$.estimatedFuelLitres").value(53.334))
        .andExpect(jsonPath("$.pendingRecordCount").value(1))
        .andExpect(jsonPath("$.accountingPosted").value(false));
  }

  @Test
  void cancelledTripsBlockApprovalButAllowRejectionOfExistingSubmissions() throws Exception {
    var f = tripFixture();
    var b = fuel(f.fuel(), now(), "1000");
    b.put("tripId", f.trip());
    String id = id(create("/api/v1/fuel/entries", b));
    f.trips()
        .action(
            "/api/v1/trips/" + f.trip() + "/cancel",
            Map.of("eventId", uid(), "reason", "Customer rescheduled"),
            200);
    review(fuelPath(id, "approve"), decision(1), 409);
    review(fuelPath(id, "reject"), decision(1), 200);
  }

  @Test
  void tripTruckOwnershipAndOccurrenceWindowAreEnforced() throws Exception {
    var f = tripFixture();
    var other = fixture();
    var wrong = fuel(other, now(), "1000");
    wrong.put("tripId", f.trip());
    action("/api/v1/fuel/entries", wrong, 400);
    var early = expense(f.fuel(), f.trip(), "TOLL");
    early.put("occurredAt", now().minusSeconds(3600).toString());
    action("/api/v1/expenses", early, 400);
    f.trips().finished(f.trip());
    var late = expense(f.fuel(), f.trip(), "TOLL");
    action("/api/v1/expenses", late, 400);
  }

  @Test
  void expenseIdentityAndReceiptUniquenessPreventDuplicateCosts() throws Exception {
    var f = fixture();
    var b = expense(f, null, "REPAIR");
    create("/api/v1/expenses", b);
    action("/api/v1/expenses", b, 200);
    b.put("expenseId", uid());
    action("/api/v1/expenses", b, 409);
  }

  @Test
  void fuelCreationRetriesPreserveOneSubmissionAndAudit() throws Exception {
    var f = fixture();
    var b = fuel(f, now(), "1000");
    String id = id(create("/api/v1/fuel/entries", b));
    action("/api/v1/fuel/entries", b, 200);
    b.put("quantityLitres", 101);
    action("/api/v1/fuel/entries", b, 409);
    assertThat(
            db.queryForObject(
                "select count(*) from audit_events where target_id=? and"
                    + " action='FUEL_ENTRY_CREATED'",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void timestampsDatesAndMissingApprovalEvidenceAreValidated() throws Exception {
    var f = fixture();
    var b = fuel(f, now().plusSeconds(3600), "1000");
    action("/api/v1/fuel/entries", b, 400);
    b = fuel(f, now(), "1000");
    b.put("filledOn", h.helpers.today().minusDays(1).toString());
    action("/api/v1/fuel/entries", b, 400);
    var e = expense(f, null, "REPAIR");
    e.remove("receiptEvidence");
    String id = id(create("/api/v1/expenses", e));
    review("/api/v1/expenses/" + id + "/approve", decision(1), 400);
  }

  int concurrent(String path, Object body) throws Exception {
    return mvc.perform(
            post(path)
                .with(user("reviewer").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  void race(String path1, Object a, String path2, Object b, int s1, int s2) throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var x =
          executor.submit(
              () -> {
                gate.await();
                return concurrent(path1, a);
              });
      var y =
          executor.submit(
              () -> {
                gate.await();
                return concurrent(path2, b);
              });
      gate.countDown();
      assertThat(List.of(x.get(45, TimeUnit.SECONDS), y.get(45, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(s1, s2);
    }
  }

  @Test
  void concurrentConflictingReviewsHaveExactlyOneWinner() throws Exception {
    var f = fixture();
    String id = id(create("/api/v1/fuel/entries", fuel(f, now(), "1000")));
    race(fuelPath(id, "approve"), decision(1), fuelPath(id, "reject"), decision(1), 200, 409);
    assertThat(
            db.queryForObject(
                "select count(*) from operating_events where record_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void concurrentInconsistentOdometersCannotBothBeApproved() throws Exception {
    var f = fixture();
    Instant at = now().minusSeconds(3600);
    String early = id(create("/api/v1/fuel/entries", fuel(f, at, "2000")));
    String late = id(create("/api/v1/fuel/entries", fuel(f, at.plusSeconds(1800), "1000")));
    race(fuelPath(early, "approve"), decision(1), fuelPath(late, "approve"), decision(1), 200, 409);
  }

  @Test
  void rolesPrivacyCsrfAndRateCorrectionPermissionsAreEnforced() throws Exception {
    for (String role : List.of("CUSTOMER", "SUPPLIER", "DRIVER"))
      mvc.perform(get("/api/v1/expenses").with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/fuel/entries").with(user("accounts").roles("ACCOUNTS")))
        .andExpect(status().isOk());
    var f = fixture();
    var correction =
        Map.of(
            "eventId",
            uid(),
            "expectedPrice",
            new BigDecimal("91.50"),
            "newPrice",
            95,
            "reason",
            "Correct published rate");
    mvc.perform(
            post("/api/v1/fuel/rates/" + f.rate() + "/corrections")
                .with(user("accounts").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(correction)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/expenses")
                .contentType("application/json")
                .content(json.writeValueAsString(expense(f, null, "REPAIR"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void concurrentRateCorrectionsRequireReloadForTheLosingWriter() throws Exception {
    var f = fixture();
    String path = "/api/v1/fuel/rates/" + f.rate() + "/corrections";
    var a =
        Map.of(
            "eventId",
            uid(),
            "expectedPrice",
            new BigDecimal("91.50"),
            "newPrice",
            94,
            "reason",
            "Pump correction A");
    var b =
        Map.of(
            "eventId",
            uid(),
            "expectedPrice",
            new BigDecimal("91.50"),
            "newPrice",
            95,
            "reason",
            "Pump correction B");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      java.util.function.Function<Object, Callable<Integer>> task =
          body ->
              () -> {
                gate.await();
                return mvc.perform(
                        post(path)
                            .with(user("rate-admin").roles("SUPER_ADMIN"))
                            .with(csrf())
                            .contentType("application/json")
                            .content(json.writeValueAsString(body)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              };
      var x = executor.submit(task.apply(a));
      var y = executor.submit(task.apply(b));
      gate.countDown();
      assertThat(List.of(x.get(45, TimeUnit.SECONDS), y.get(45, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
            db.queryForObject(
                "select count(*) from diesel_rate_corrections where rate_id=?",
                Integer.class,
                UUID.fromString(f.rate())))
        .isEqualTo(1);
  }

  Map<String, Object> reviewBody() {
    return Map.of("eventId", uid(), "reason", "Physical record independently checked");
  }

  @Test
  void approvedOdometerResetAllowsALowerLaterReading() throws Exception {
    var f = fixture();
    Instant first = now().minusSeconds(180);
    String earlier = id(create("/api/v1/fuel/entries", fuel(f, first, "1000.0")));
    review(fuelPath(earlier, "approve"), decision(1), 200);
    var reset =
        Map.of(
            "resetId",
            uid(),
            "truckId",
            f.truck(),
            "newReading",
            new BigDecimal("50.0"),
            "resetAt",
            now().minusSeconds(90).toString(),
            "reason",
            "Dashboard cluster replaced",
            "evidenceReference",
            "Workshop replacement note");
    action("/api/v1/odometer-resets", reset, 201);
    action("/api/v1/odometer-resets", reset, 200);
    review("/api/v1/odometer-resets/" + reset.get("resetId") + "/approve", reviewBody(), 200);
    String later = id(create("/api/v1/fuel/entries", fuel(f, now().minusSeconds(30), "80.0")));
    review(fuelPath(later, "approve"), decision(1), 200);
    String tooLow = id(create("/api/v1/fuel/entries", fuel(f, now().minusSeconds(10), "40.0")));
    review(fuelPath(tooLow, "approve"), decision(1), 409);
  }

  @Test
  void truckOnlyFuelCanBeSplitAcrossTripsAfterIndependentApproval() throws Exception {
    var f = tripFixture();
    String fuel = id(create("/api/v1/fuel/entries", fuel(f.fuel(), now(), "1000.0")));
    review(fuelPath(fuel, "approve"), decision(1), 200);
    var allocation =
        Map.of(
            "allocationId",
            uid(),
            "tripId",
            f.trip(),
            "quantityLitres",
            new BigDecimal("40"),
            "reason",
            "Share pump receipt with this loaded trip",
            "evidenceReference",
            "Supervisor allocation note");
    action("/api/v1/fuel/entries/" + fuel + "/allocations", allocation, 201);
    review(
        "/api/v1/fuel-allocations/" + allocation.get("allocationId") + "/approve",
        reviewBody(),
        200);
    mvc.perform(get("/api/v1/trips/" + f.trip() + "/operating-costs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvedFuelPurchasedLitres").value(40))
        .andExpect(jsonPath("$.approvedFuelCost").value(3680));
    var assigned = fuel(f.fuel(), now(), "1100.0");
    assigned.put("tripId", f.trip());
    String whole = id(create("/api/v1/fuel/entries", assigned));
    review(fuelPath(whole, "approve"), decision(1), 200);
    action(
        "/api/v1/fuel/entries/" + whole + "/allocations",
        Map.of(
            "allocationId",
            uid(),
            "tripId",
            f.trip(),
            "quantityLitres",
            new BigDecimal("10"),
            "reason",
            "Whole receipt cannot be split",
            "evidenceReference",
            "Note"),
        409);
  }
}
