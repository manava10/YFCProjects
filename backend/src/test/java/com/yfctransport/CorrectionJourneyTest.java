package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.*;
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
class CorrectionJourneyTest {
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

  Map<String, Object> decision() {
    return Map.of("eventId", uid(), "reason", "Physical record independently checked");
  }

  int review(String path, String user) throws Exception {
    return mvc.perform(
            post(path)
                .with(user(user).roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(decision())))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  BigDecimal onHand(String yard) {
    return db.queryForObject(
        "select coalesce(sum(on_hand),0) from stock_balances where yard_id=? and material_id=?",
        BigDecimal.class,
        UUID.fromString(yard),
        UUID.fromString(f.sales().first()));
  }

  String emptyYard() throws Exception {
    return h.id(
        h.create(
            "/api/v1/yards",
            Map.of(
                "branchId",
                f.sales().branch(),
                "code",
                h.helpers.code(),
                "name",
                "Opening yard",
                "address",
                "Empty yard")));
  }

  @Test
  void openingStockIsOneTimeAndEntersOnHandAfterIndependentApproval() throws Exception {
    String yard = emptyYard();
    var body =
        new HashMap<>(
            Map.of(
                "openingId",
                uid(),
                "yardId",
                yard,
                "materialId",
                f.sales().first(),
                "quantity",
                new BigDecimal("12.500"),
                "occurredOn",
                h.helpers.today().toString(),
                "reason",
                "Counted opening stock from physical books",
                "evidenceReference",
                "Signed opening count sheet"));
    h.action("/api/v1/stock-openings", body, 201);
    h.action("/api/v1/stock-openings", body, 200);
    assertThat(review("/api/v1/stock-openings/" + body.get("openingId") + "/approve", "stock-submitter"))
        .isEqualTo(409);
    assertThat(review("/api/v1/stock-openings/" + body.get("openingId") + "/approve", "reviewer"))
        .isEqualTo(200);
    assertThat(onHand(yard)).isEqualByComparingTo("12.500");
    var second = new HashMap<>(body);
    second.put("openingId", uid());
    h.action("/api/v1/stock-openings", second, 409);
  }

  @Test
  void sameBranchTransferMovesAvailableStockOnceApproved() throws Exception {
    String source = emptyYard(), dest = emptyYard();
    db.update(
        "insert into stock_balances(yard_id,material_id,on_hand,reserved) values(?,?,?,0)",
        UUID.fromString(source),
        UUID.fromString(f.sales().first()),
        new BigDecimal("10"));
    var body =
        new HashMap<>(
            Map.of(
                "transferId",
                uid(),
                "fromYardId",
                source,
                "toYardId",
                dest,
                "materialId",
                f.sales().first(),
                "quantity",
                new BigDecimal("4"),
                "occurredOn",
                h.helpers.today().toString(),
                "reason",
                "Balance yard inventory for dispatch",
                "evidenceReference",
                "Internal transfer note"));
    h.action("/api/v1/stock-transfers", body, 201);
    assertThat(review("/api/v1/stock-transfers/" + body.get("transferId") + "/approve", "reviewer"))
        .isEqualTo(200);
    assertThat(onHand(source)).isEqualByComparingTo("6");
    assertThat(onHand(dest)).isEqualByComparingTo("4");
  }

  @Test
  void approvedCountIncreaseCanBeReversedByADifferentReviewer() throws Exception {
    String yard = emptyYard();
    var count =
        new HashMap<>(
            Map.of(
                "adjustmentId",
                uid(),
                "kind",
                "INCREASE",
                "yardId",
                yard,
                "materialId",
                f.sales().first(),
                "quantity",
                new BigDecimal("5"),
                "expectedOnHand",
                BigDecimal.ZERO,
                "occurredOn",
                h.helpers.today().toString(),
                "reason",
                "Physical count found extra material",
                "evidenceReference",
                "Count sheet"));
    h.action("/api/v1/stock-adjustments", count, 201);
    assertThat(
            review(
                "/api/v1/stock-adjustments/" + count.get("adjustmentId") + "/approve", "reviewer"))
        .isEqualTo(200);
    assertThat(onHand(yard)).isEqualByComparingTo("5");
    var reversal =
        Map.of(
            "reversalId",
            uid(),
            "reason",
            "Count was posted against the wrong yard pile",
            "evidenceReference",
            "Supervisor correction note");
    h.action("/api/v1/stock-adjustments/" + count.get("adjustmentId") + "/reversals", reversal, 201);
    assertThat(review("/api/v1/stock-reversals/" + reversal.get("reversalId") + "/approve", "reviewer"))
        .isEqualTo(200);
    assertThat(onHand(yard)).isZero();
  }

  @Test
  void supplierReturnLeavesYardStockAfterIndependentApproval() throws Exception {
    String purchase = h.purchase(f, "YARD", "10");
    var receipt = h.receipt("10", "10", "10");
    h.receive(purchase, receipt, 201);
    BigDecimal before = onHand(f.yard());
    var body =
        new HashMap<>(
            Map.of(
                "returnId",
                uid(),
                "quantity",
                new BigDecimal("2"),
                "occurredOn",
                h.helpers.today().toString(),
                "reason",
                "Supplier collected rejected material",
                "evidenceReference",
                "Supplier collection note"));
    h.action("/api/v1/purchases/" + purchase + "/supplier-returns", body, 201);
    assertThat(review("/api/v1/supplier-returns/" + body.get("returnId") + "/approve", "reviewer"))
        .isEqualTo(200);
    assertThat(onHand(f.yard())).isEqualByComparingTo(before.subtract(new BigDecimal("2")));
    var extra = new HashMap<>(body);
    extra.put("returnId", uid());
    extra.put("quantity", new BigDecimal("9"));
    h.action("/api/v1/purchases/" + purchase + "/supplier-returns", extra, 409);
  }

  @Test
  void operationsCannotSelfReviewAndExternalRolesAreDenied() throws Exception {
    String yard = emptyYard();
    var body =
        Map.of(
            "openingId",
            uid(),
            "yardId",
            yard,
            "materialId",
            f.sales().first(),
            "quantity",
            new BigDecimal("1"),
            "occurredOn",
            h.helpers.today().toString(),
            "reason",
            "Opening",
            "evidenceReference",
            "Sheet");
    h.action("/api/v1/stock-openings", body, 201);
    mvc.perform(get("/api/v1/stock-openings/" + body.get("openingId")).with(user("driver").roles("DRIVER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void yardReceiptReversalRemovesAcceptedStockAfterIndependentApproval() throws Exception {
    String purchase = h.purchase(f, "YARD", "10");
    var receipt = h.receipt("10", "10", "10");
    h.receive(purchase, receipt, 201);
    assertThat(onHand(f.yard())).isEqualByComparingTo("10");
    var body =
        Map.of(
            "reversalId",
            uid(),
            "reason",
            "Receipt recorded against the wrong purchase",
            "evidenceReference",
            "Signed receipt reversal note");
    h.action("/api/v1/purchase-receipts/" + receipt.get("receiptId") + "/reversals", body, 201);
    h.action("/api/v1/purchase-receipts/" + receipt.get("receiptId") + "/reversals", body, 200);
    assertThat(
            review(
                "/api/v1/purchase-receipt-reversals/" + body.get("reversalId") + "/approve",
                "reviewer"))
        .isEqualTo(200);
    assertThat(onHand(f.yard())).isEqualByComparingTo("0");
    assertThat(
            db.queryForObject(
                "select received_quantity from purchase_orders where id=?",
                BigDecimal.class,
                UUID.fromString(purchase)))
        .isEqualByComparingTo("0");
  }

  @Test
  void receiptReversalApprovalConflictsWhenBillingAlreadyClaimedTheReceipt() throws Exception {
    String purchase = h.purchase(f, "YARD", "10");
    var receipt = h.receipt("10", "10", "10");
    h.receive(purchase, receipt, 201);
    String receiptId = receipt.get("receiptId").toString();
    h.action(
        "/api/v1/accounting/billing-preparations",
        new HashMap<>(
            Map.of(
                "preparationId",
                uid(),
                "kind",
                "PURCHASE_RECEIPT",
                "sourceId",
                receiptId,
                "preparedOn",
                h.helpers.today().toString(),
                "quantityBasis",
                "ACCEPTED",
                "reference",
                uid(),
                "evidenceReference",
                "Accepted quotation / supplier agreement reference",
                "basisReason",
                "Agreed quantity basis checked against the source records")),
        201);
    UUID reversalId = UUID.randomUUID();
    db.update(
        "insert into"
            + " purchase_receipt_reversals(id,receipt_id,reason,evidence_reference,submitted_by,create_payload)"
            + " values(?,?,?,?,?,?)",
        reversalId,
        UUID.fromString(receiptId),
        "Receipt recorded against the wrong purchase",
        "Signed receipt reversal note",
        "stock-submitter",
        "[]");
    db.update(
        "insert into purchase_receipt_reversal_claims(receipt_id,reversal_id) values(?,?)",
        UUID.fromString(receiptId),
        reversalId);
    assertThat(
            review("/api/v1/purchase-receipt-reversals/" + reversalId + "/approve", "reviewer"))
        .isEqualTo(409);
    assertThat(onHand(f.yard())).isEqualByComparingTo("10");
  }

  String otherBranch() throws Exception {
    return h.id(
        h.create(
            "/api/v1/branches",
            Map.of(
                "code",
                h.helpers.code(),
                "name",
                "Other branch",
                "address",
                "Office",
                "city",
                "Gurgaon",
                "state",
                "Haryana",
                "pincode",
                "122001")));
  }

  @Test
  void crossBranchOperatingTransferMovesAvailableStockOnceApproved() throws Exception {
    String source = emptyYard();
    String dest =
        h.id(
            h.create(
                "/api/v1/yards",
                Map.of(
                    "branchId",
                    otherBranch(),
                    "code",
                    h.helpers.code(),
                    "name",
                    "Other branch yard",
                    "address",
                    "Second branch yard")));
    db.update(
        "insert into stock_balances(yard_id,material_id,on_hand,reserved) values(?,?,?,0)",
        UUID.fromString(source),
        UUID.fromString(f.sales().first()),
        new BigDecimal("10"));
    var body =
        new HashMap<>(
            Map.of(
                "transferId",
                uid(),
                "fromYardId",
                source,
                "toYardId",
                dest,
                "materialId",
                f.sales().first(),
                "quantity",
                new BigDecimal("3"),
                "occurredOn",
                h.helpers.today().toString(),
                "reason",
                "Move usable stock to the other operating branch",
                "evidenceReference",
                "Cross-branch transfer note"));
    h.action("/api/v1/stock-transfers", body, 201);
    assertThat(review("/api/v1/stock-transfers/" + body.get("transferId") + "/approve", "reviewer"))
        .isEqualTo(200);
    assertThat(onHand(source)).isEqualByComparingTo("7");
    assertThat(onHand(dest)).isEqualByComparingTo("3");
  }

  @Test
  void quarantineYardsCannotReceivePurchasesAndNeedSameBranchToLeaveOperatingStock()
      throws Exception {
    String quarantine =
        h.id(
            h.create(
                "/api/v1/yards",
                Map.of(
                    "branchId",
                    f.sales().branch(),
                    "code",
                    h.helpers.code(),
                    "name",
                    "Hold yard",
                    "address",
                    "Quarantine bay",
                    "kind",
                    "QUARANTINE")));
    var purchase = h.purchaseBody(f, "YARD", "10");
    purchase.put("yardId", quarantine);
    h.action("/api/v1/purchases", purchase, 409);
    String other =
        h.id(
            h.create(
                "/api/v1/yards",
                Map.of(
                    "branchId",
                    otherBranch(),
                    "code",
                    h.helpers.code(),
                    "name",
                    "Other quarantine",
                    "address",
                    "Other bay",
                    "kind",
                    "QUARANTINE")));
    db.update(
        "insert into stock_balances(yard_id,material_id,on_hand,reserved) values(?,?,?,0)",
        UUID.fromString(f.yard()),
        UUID.fromString(f.sales().first()),
        new BigDecimal("4"));
    var body =
        new HashMap<>(
            Map.of(
                "transferId",
                uid(),
                "fromYardId",
                f.yard(),
                "toYardId",
                other,
                "materialId",
                f.sales().first(),
                "quantity",
                new BigDecimal("1"),
                "occurredOn",
                h.helpers.today().toString(),
                "reason",
                "Cannot send quarantine stock across branches",
                "evidenceReference",
                "Rejected transfer note"));
    h.action("/api/v1/stock-transfers", body, 409);
  }
}
