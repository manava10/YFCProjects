package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.*;
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
@WithMockUser(username = "billing-preparer", roles = "SUPER_ADMIN")
class AllocationJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  BillingPreparationJourneyTest b;
  String period, partyAccount, incomeAccount, bankAccount, customerProfile;

  static final String ALLOCATIONS = "/api/v1/accounting/allocations";

  @BeforeEach
  void setup() throws Exception {
    b = new BillingPreparationJourneyTest();
    b.mvc = mvc;
    b.json = json;
    b.db = db;
    b.setup();
    period = createPeriod();
    partyAccount = createAccount("ASSET");
    incomeAccount = createAccount("INCOME");
    bankAccount = createAccount("ASSET");
    b.h.create(
        "/api/v1/accounting/money-accounts",
        Map.of(
            "accountId",
            bankAccount,
            "branchId",
            b.f.sales().branch(),
            "kind",
            "BANK",
            "reference",
            "Primary bank account"));
    customerProfile = uid();
    b.h.create(
        "/api/v1/accounting/parties",
        Map.of(
            "profileId",
            customerProfile,
            "partyType",
            "CUSTOMER",
            "partyId",
            b.f.sales().customer(),
            "accountId",
            partyAccount,
            "paymentTermsDays",
            30,
            "creditLimit",
            new BigDecimal("500000.00")));
  }

  String uid() {
    return UUID.randomUUID().toString();
  }

  String createAccount(String type) throws Exception {
    String id = uid();
    b.h.create(
        "/api/v1/accounting/accounts",
        Map.of(
            "accountId",
            id,
            "code",
            "A" + uid().replace("-", "").substring(0, 7).toUpperCase(Locale.ROOT),
            "name",
            "Account " + type,
            "type",
            type));
    return id;
  }

  String createPeriod() throws Exception {
    var today = b.h.helpers.today();
    var existing =
        db.queryForList(
            "select id from financial_periods where status='OPEN' and starts_on<=? and ends_on>=?",
            today,
            today);
    if (!existing.isEmpty()) return existing.getFirst().values().iterator().next().toString();
    String id = uid();
    b.h.create(
        "/api/v1/accounting/periods",
        Map.of(
            "periodId",
            id,
            "code",
            "P" + uid().replace("-", "").substring(0, 7).toUpperCase(Locale.ROOT),
            "startsOn",
            today.minusDays(10).toString(),
            "endsOn",
            today.plusDays(10).toString()));
    return id;
  }

  record PostedBill(String preparationId, String journalId, BigDecimal payableTotal) {}

  void assessZeroTax(String prepId) throws Exception {
    var prep = b.getOne(prepId);
    List<Map<String, Object>> lines = prep.read("$.lines");
    var taxLines = new ArrayList<Map<String, Object>>();
    for (var line : lines) {
      int pos = ((Number) line.get("position")).intValue();
      if (new BigDecimal(line.get("material_amount").toString()).signum() > 0)
        taxLines.add(
            Map.of(
                "position",
                pos,
                "component",
                "MATERIAL",
                "hsnOrSac",
                "2517",
                "ratePercent",
                "0.00"));
      if (new BigDecimal(line.get("transport_amount").toString()).signum() > 0)
        taxLines.add(
            Map.of(
                "position",
                pos,
                "component",
                "TRANSPORT",
                "hsnOrSac",
                "996511",
                "ratePercent",
                "0.00"));
    }
    mvc.perform(
            post(BillingPreparationJourneyTest.API + "/" + prepId + "/tax")
                .with(user("reviewer").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("eventId", uid(), "supply", "INTRA_STATE", "lines", taxLines))))
        .andExpect(status().isCreated());
  }

  PostedBill postedSalesBill() throws Exception {
    String prepId = b.create(b.sale());
    assertThat(b.review(prepId, "ready", b.event(), "reviewer")).isEqualTo(200);
    assessZeroTax(prepId);
    String journalId = uid();
    b.h.action(
        BillingPreparationJourneyTest.API + "/" + prepId + "/journals",
        Map.of(
            "journalId",
            journalId,
            "periodId",
            period,
            "entryDate",
            b.h.helpers.today().toString(),
            "counterAccountId",
            incomeAccount,
            "description",
            "Sales invoice posting",
            "reference",
            uid()),
        201);
    postJournal(journalId);
    BigDecimal payable = b.getOne(prepId).read("$.payable_total", BigDecimal.class);
    return new PostedBill(prepId, journalId, payable);
  }

  String createReceipt(String amount, boolean postIt) throws Exception {
    return createVoucher("CUSTOMER_RECEIPT", customerProfile, amount, postIt);
  }

  String createVoucher(String kind, String profileId, String amount, boolean postIt)
      throws Exception {
    String voucherId = uid();
    b.h.create(
        "/api/v1/accounting/money-vouchers",
        Map.of(
            "voucherId",
            voucherId,
            "profileId",
            profileId,
            "moneyAccountId",
            bankAccount,
            "periodId",
            period,
            "branchId",
            b.f.sales().branch(),
            "kind",
            kind,
            "entryDate",
            b.h.helpers.today().toString(),
            "amount",
            new BigDecimal(amount),
            "reference",
            uid(),
            "evidenceReference",
            "UTR-" + uid().substring(0, 8)));
    if (postIt) {
      mvc.perform(
              post("/api/v1/accounting/money-vouchers/" + voucherId + "/post")
                  .with(user("ledger-reviewer").roles("ACCOUNTS"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          Map.of("eventId", uid(), "reason", "Bank credit verified"))))
          .andExpect(status().isOk());
    }
    return voucherId;
  }

  void postJournal(String journalId) throws Exception {
    mvc.perform(
            post("/api/v1/accounting/journals/" + journalId + "/post")
                .with(user("ledger-reviewer").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("eventId", uid(), "reason", "Journal checked"))))
        .andExpect(status().isOk());
  }

  int postAllocation(Map<String, Object> body) throws Exception {
    return mvc.perform(
            post(ALLOCATIONS)
                .with(user("accounts-user").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  DocumentContext getVoucher(String id) throws Exception {
    return JsonPath.parse(
        mvc.perform(get("/api/v1/accounting/money-vouchers/" + id))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void partialAndFullAllocationSettlesBillAndSupportsIdempotentReplayAndReversal() throws Exception {
    var bill = postedSalesBill();
    assertThat(b.getOne(bill.preparationId()).read("$.payment_status", String.class))
        .isEqualTo("UNPAID");
    assertThat(b.getOne(bill.preparationId()).read("$.outstanding_amount", BigDecimal.class))
        .isEqualByComparingTo("120000.00");

    String voucher1 = createReceipt("70000.00", true);
    String alloc1 = uid();
    var body1 =
        Map.<String, Object>of(
            "allocationId",
            alloc1,
            "voucherId",
            voucher1,
            "preparationId",
            bill.preparationId(),
            "amount",
            new BigDecimal("70000.00"),
            "note",
            "First part payment");
    assertThat(postAllocation(body1)).isEqualTo(201);
    assertThat(postAllocation(body1)).isEqualTo(200);

    var afterPart = b.getOne(bill.preparationId());
    assertThat(afterPart.read("$.payment_status", String.class)).isEqualTo("PARTIALLY_PAID");
    assertThat(afterPart.read("$.allocated_amount", BigDecimal.class))
        .isEqualByComparingTo("70000.00");
    assertThat(afterPart.read("$.outstanding_amount", BigDecimal.class))
        .isEqualByComparingTo("50000.00");

    var v1 = getVoucher(voucher1);
    assertThat(v1.read("$.allocated_amount", BigDecimal.class)).isEqualByComparingTo("70000.00");
    assertThat(v1.read("$.unallocated_amount", BigDecimal.class)).isEqualByComparingTo("0.00");

    // Second receipt settles the remaining 50,000 (with 10,000 left unallocated on voucher2)
    String voucher2 = createReceipt("60000.00", true);
    assertThat(
            postAllocation(
                Map.of(
                    "allocationId",
                    uid(),
                    "voucherId",
                    voucher2,
                    "preparationId",
                    bill.preparationId(),
                    "amount",
                    new BigDecimal("50000.00"),
                    "note",
                    "Final settlement")))
        .isEqualTo(201);

    var settled = b.getOne(bill.preparationId());
    assertThat(settled.read("$.payment_status", String.class)).isEqualTo("PAID");
    assertThat(settled.read("$.outstanding_amount", BigDecimal.class))
        .isEqualByComparingTo("0.00");
    assertThat(getVoucher(voucher2).read("$.unallocated_amount", BigDecimal.class))
        .isEqualByComparingTo("10000.00");

    // Reversing the voucher while active allocation exists is blocked
    mvc.perform(
            post("/api/v1/accounting/money-vouchers/" + voucher1 + "/reversals")
                .with(user("accounts-user").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "journalId",
                            uid(),
                            "periodId",
                            period,
                            "entryDate",
                            b.h.helpers.today().toString(),
                            "reason",
                            "Mistake",
                            "reference",
                            uid()))))
        .andExpect(status().isConflict());

    // Reverse alloc1 (and verify idempotent replay)
    var revBody = Map.of("eventId", uid(), "reason", "Reallocate to another invoice");
    mvc.perform(
            post(ALLOCATIONS + "/" + alloc1 + "/reverse")
                .with(user("accounts-user").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(revBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVERSED"));
    mvc.perform(
            post(ALLOCATIONS + "/" + alloc1 + "/reverse")
                .with(user("accounts-user").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(revBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(true));

    assertThat(b.getOne(bill.preparationId()).read("$.payment_status", String.class))
        .isEqualTo("PARTIALLY_PAID");
    assertThat(b.getOne(bill.preparationId()).read("$.outstanding_amount", BigDecimal.class))
        .isEqualByComparingTo("70000.00");
    assertThat(getVoucher(voucher1).read("$.unallocated_amount", BigDecimal.class))
        .isEqualByComparingTo("70000.00");

    // Now voucher1 can be reversed cleanly
    mvc.perform(
            post("/api/v1/accounting/money-vouchers/" + voucher1 + "/reversals")
                .with(user("accounts-user").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "journalId",
                            uid(),
                            "periodId",
                            period,
                            "entryDate",
                            b.h.helpers.today().toString(),
                            "reason",
                            "Bounced cheque",
                            "reference",
                            uid()))))
        .andExpect(status().isCreated());

    // List endpoint filtering works
    mvc.perform(
            get(ALLOCATIONS)
                .param("preparationId", bill.preparationId())
                .param("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void rejectsOverAllocationUnpostedRecordsRefundsAndUnauthorizedRoles() throws Exception {
    var bill = postedSalesBill();
    String draftReceipt = createReceipt("10000.00", false);
    String postedReceipt = createReceipt("50000.00", true);
    String refund = createVoucher("CUSTOMER_REFUND", customerProfile, "10000.00", false);

    // Draft voucher blocked
    assertThat(
            postAllocation(
                Map.of(
                    "allocationId",
                    uid(),
                    "voucherId",
                    draftReceipt,
                    "preparationId",
                    bill.preparationId(),
                    "amount",
                    new BigDecimal("1000.00"))))
        .isEqualTo(409);

    // Refund voucher blocked
    assertThat(
            postAllocation(
                Map.of(
                    "allocationId",
                    uid(),
                    "voucherId",
                    refund,
                    "preparationId",
                    bill.preparationId(),
                    "amount",
                    new BigDecimal("1000.00"))))
        .isEqualTo(409);

    // Over-allocating beyond voucher amount blocked
    assertThat(
            postAllocation(
                Map.of(
                    "allocationId",
                    uid(),
                    "voucherId",
                    postedReceipt,
                    "preparationId",
                    bill.preparationId(),
                    "amount",
                    new BigDecimal("50000.01"))))
        .isEqualTo(409);

    // Non-accounts roles are forbidden
    mvc.perform(get(ALLOCATIONS).with(user("ops").roles("OPERATIONS")))
        .andExpect(status().isForbidden());
    mvc.perform(get(ALLOCATIONS).with(user("cust").roles("CUSTOMER")))
        .andExpect(status().isForbidden());
  }
}
