package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "compliance-admin", roles = "SUPER_ADMIN")
class MultiStateComplianceAndReportsJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  SalesJourneyTest sales;

  @BeforeEach
  void setup() {
    sales = new SalesJourneyTest();
    sales.mvc = mvc;
    sales.json = json;
  }

  String uid() {
    return UUID.randomUUID().toString();
  }

  String code(String prefix) {
    return prefix + uid().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
  }

  @Test
  void multiStateBranchInvoiceSeriesLegalDataAndEnterpriseReportsWorkEndToEnd() throws Exception {
    // 1. Create a Rajasthan branch (08) with GSTIN & bank details, and a Haryana branch (06)
    String rjBranchJson =
        mvc.perform(
                post("/api/v1/branches")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code", code("RJ"),
                                "name", "Kotputli Rajasthan HQ",
                                "address", "NH-48 Crusher Zone, Kotputli",
                                "city", "Kotputli",
                                "state", "Rajasthan",
                                "pincode", "303108",
                                "gstin", "08ABCDE1234F1Z5",
                                "bankName", "HDFC Bank Kotputli",
                                "bankAccount", "50200012345678",
                                "bankIfsc", "HDFC0001234"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.gstin").value("08ABCDE1234F1Z5"))
            .andExpect(jsonPath("$.stateCode").value("08"))
            .andExpect(jsonPath("$.bankIfsc").value("HDFC0001234"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String rjBranchId = JsonPath.read(rjBranchJson, "$.id");

    // Reject mismatched stateCode vs GSTIN
    mvc.perform(
            post("/api/v1/branches")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "code", code("BAD"),
                            "name", "Mismatch Branch",
                            "address", "Road 1",
                            "city", "Gurugram",
                            "state", "Haryana",
                            "pincode", "122001",
                            "gstin", "06ABCDE1234F1Z9",
                            "stateCode", "08"))))
        .andExpect(status().isBadRequest());

    String hrBranchJson =
        mvc.perform(
                post("/api/v1/branches")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code", code("HR"),
                                "name", "Gurugram Haryana Depot",
                                "address", "Sector 82, Gurugram",
                                "city", "Gurugram",
                                "state", "Haryana",
                                "pincode", "122004",
                                "gstin", "06ABCDE1234F1Z9"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.stateCode").value("06"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String hrBranchId = JsonPath.read(hrBranchJson, "$.id");

    // 2. Create a Haryana customer (06) — buying from Rajasthan branch (08) -> INTER_STATE suggested!
    String hrCustomerJson =
        mvc.perform(
                post("/api/v1/customers")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "name", "Gurugram Infra Pvt Ltd",
                                "mobile", "9876543210",
                                "gstin", "06XYZAB9876C1Z2"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String hrCustomerId = JsonPath.read(hrCustomerJson, "$.id");

    String projectJson =
        mvc.perform(
                post("/api/v1/customers/" + hrCustomerId + "/projects")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "branchId", rjBranchId,
                                "code", code("PRJ"),
                                "name", "NH48 Package 1"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String projectId = JsonPath.read(projectJson, "$.id");

    String siteJson =
        mvc.perform(
                post("/api/v1/customers/" + hrCustomerId + "/projects/" + projectId + "/sites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code", code("SIT"),
                                "name", "Site Gate 1",
                                "address", "Sector 82 Gurugram"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String siteId = JsonPath.read(siteJson, "$.id");

    String matJson =
        mvc.perform(
                post("/api/v1/materials")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code", code("AGG"),
                                "name", "20 MM Aggregate",
                                "category", "AGGREGATES"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String matId = JsonPath.read(matJson, "$.id");

    // Create RFQ -> Quotation -> Order
    String today = LocalDate.now().toString();
    String rfqJson =
        mvc.perform(
                post("/api/v1/rfqs")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "customerId", hrCustomerId,
                                "deliverySiteId", siteId,
                                "requiredDate", LocalDate.now().plusDays(2).toString(),
                                "businessType", "MATERIAL_AND_TRANSPORT",
                                "items", List.of(Map.of("materialId", matId, "quantity", "20.000"))))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String rfqId = JsonPath.read(rfqJson, "$.id");
    String rfqItemId = JsonPath.read(rfqJson, "$.items[0].id");

    String quoteJson =
        mvc.perform(
                post("/api/v1/rfqs/" + rfqId + "/quotations")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "validUntil", LocalDate.now().plusDays(7).toString(),
                                "paymentTerms", "15 days net",
                                "deliveryTerms", "Ex-Kotputli delivered Gurugram",
                                "items",
                                List.of(
                                    Map.of(
                                        "rfqItemId", rfqItemId,
                                        "materialRate", "800.00",
                                        "transportRate", "400.00"))))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String quoteId = JsonPath.read(quoteJson, "$.id");

    mvc.perform(post("/api/v1/quotations/" + quoteId + "/send").with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/quotations/" + quoteId + "/accept")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "customerContact", "Anil Sharma",
                            "approvalReference", "PO-HR-001"))))
        .andExpect(status().isOk());
    String orderJson =
        mvc.perform(post("/api/v1/quotations/" + quoteId + "/convert").with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String orderId = JsonPath.read(orderJson, "$.orderId");

    // 3. Create Billing Preparation -> Mark Ready -> Assess IGST (by second user)
    String prepId = uid();
    mvc.perform(
            post("/api/v1/accounting/billing-preparations")
                .with(user("maker-acct").roles("ACCOUNTS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "preparationId", prepId,
                            "kind", "SALES_ORDER",
                            "sourceId", orderId,
                            "preparedOn", today,
                            "quantityBasis", "ORDERED",
                            "reference", "INV-REF-01",
                            "evidenceReference", "WB-001",
                            "basisReason", "Ordered tonnage"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.branch.gstin").value("08ABCDE1234F1Z5"))
        .andExpect(jsonPath("$.party_gstin").value("06XYZAB9876C1Z2"))
        .andExpect(jsonPath("$.suggested_supply").value("INTER_STATE"));

    mvc.perform(
            post("/api/v1/accounting/billing-preparations/" + prepId + "/ready")
                .with(user("checker-acct").roles("ACCOUNTS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid(), "reason", "Verified"))))
        .andExpect(status().isOk());

    String taxedJson =
        mvc.perform(
                post("/api/v1/accounting/billing-preparations/" + prepId + "/tax")
                    .with(user("checker-acct").roles("ACCOUNTS"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "eventId", uid(),
                                "supply", "INTER_STATE",
                                "lines",
                                List.of(
                                    Map.of(
                                        "position", 1,
                                        "component", "MATERIAL",
                                        "hsnOrSac", "2517",
                                        "ratePercent", "5.00"),
                                    Map.of(
                                        "position", 1,
                                        "component", "TRANSPORT",
                                        "hsnOrSac", "996511",
                                        "ratePercent", "5.00"))))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String invNo = JsonPath.read(taxedJson, "$.invoice_number");
    assertThat(invNo).matches("SI-\\d{4}-08-\\d{5}");
    assertThat(invNo.length()).isLessThanOrEqualTo(16);

    // 4. Verify GSTR-1 report returns our B2B invoice + HSN summary
    mvc.perform(
            get("/api/v1/accounting/reports/gstr1")
                .param("from", today)
                .param("to", today)
                .param("branchId", rjBranchId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoiceCount").value(1))
        .andExpect(jsonPath("$.invoices[0].invoice_number").value(invNo))
        .andExpect(jsonPath("$.invoices[0].category").value("B2B"))
        .andExpect(jsonPath("$.invoices[0].branch_gstin").value("08ABCDE1234F1Z5"))
        .andExpect(jsonPath("$.hsnSummary.length()").value(2));

    // Verify Haryana branch GSTR-1 filter returns 0 invoices (proper branch isolation!)
    mvc.perform(
            get("/api/v1/accounting/reports/gstr1")
                .param("from", today)
                .param("to", today)
                .param("branchId", hrBranchId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoiceCount").value(0));

    // 5. Verify P&L, Balance Sheet, and Truck Profitability endpoints respond cleanly
    mvc.perform(
            get("/api/v1/accounting/reports/profit-and-loss")
                .param("from", today)
                .param("to", today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("INR"));

    mvc.perform(get("/api/v1/accounting/reports/balance-sheet").param("asOf", today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balanced").value(true));

    mvc.perform(
            get("/api/v1/accounting/reports/truck-profitability")
                .param("from", today)
                .param("to", today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("INR"));

    // 6. Verify E-Way Bill attachment & expiry alerts endpoint
    String truckJson =
        mvc.perform(
                post("/api/v1/trucks")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "registration", "RJ14" + code("TR").substring(0, 6),
                                "capacityTonnes", "35.000",
                                "tankCapacityLitres", "300.000",
                                "mileageKmPerLitre", "4.000"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String truckId = JsonPath.read(truckJson, "$.id");

    mvc.perform(
            put("/api/v1/trucks/" + truckId + "/compliance")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "branchId", rjBranchId,
                            "ownerName", "YFC Transport",
                            "truckType", "TIPPER",
                            "rcNumber", "RC-" + code("T"),
                            "insuranceNumber", "INS-" + code("T"),
                            "insuranceExpiry", LocalDate.now().plusYears(1).toString(),
                            "fitnessExpiry", LocalDate.now().plusYears(1).toString(),
                            "permitExpiry", LocalDate.now().plusYears(1).toString(),
                            "pollutionExpiry", LocalDate.now().plusYears(1).toString(),
                            "status", "ACTIVE"))))
        .andExpect(status().isOk());

    String driverJson =
        mvc.perform(
                post("/api/v1/drivers")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "branchId", rjBranchId,
                                "name", "Ramesh Driver",
                                "mobile", "9811122233",
                                "licenseNumber", "RJ14-" + code("DL"),
                                "licenseExpiry", LocalDate.now().plusYears(2).toString(),
                                "emergencyContact", "Suresh",
                                "emergencyMobile", "9811122244",
                                "active", true))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String driverId = JsonPath.read(driverJson, "$.id");

    // Transport-only order for quick trip allocation
    String trRfq =
        mvc.perform(
                post("/api/v1/rfqs")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "customerId", hrCustomerId,
                                "deliverySiteId", siteId,
                                "requiredDate", LocalDate.now().plusDays(1).toString(),
                                "businessType", "TRANSPORT",
                                "items", List.of(Map.of("materialId", matId, "quantity", "20.000"))))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String trRfqId = JsonPath.read(trRfq, "$.id");
    String trRfqItemId = JsonPath.read(trRfq, "$.items[0].id");
    String trQuote =
        mvc.perform(
                post("/api/v1/rfqs/" + trRfqId + "/quotations")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "validUntil", LocalDate.now().plusDays(5).toString(),
                                "paymentTerms", "Immediate",
                                "deliveryTerms", "Tipper transport",
                                "items",
                                List.of(
                                    Map.of(
                                        "rfqItemId", trRfqItemId,
                                        "materialRate", "0.00",
                                        "transportRate", "450.00"))))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String trQuoteId = JsonPath.read(trQuote, "$.id");
    mvc.perform(post("/api/v1/quotations/" + trQuoteId + "/send").with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/quotations/" + trQuoteId + "/accept")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "customerContact", "Anil Sharma",
                            "approvalReference", "PO-TR-002"))))
        .andExpect(status().isOk());
    String trOrderConv =
        mvc.perform(post("/api/v1/quotations/" + trQuoteId + "/convert").with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String trOrderId = JsonPath.read(trOrderConv, "$.orderId");
    String trOrder =
        mvc.perform(get("/api/v1/orders/" + trOrderId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String trOrderItemId = JsonPath.read(trOrder, "$.items[0].id");

    String tripId = uid();
    mvc.perform(
            post("/api/v1/trips")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "tripId", tripId,
                            "orderItemId", trOrderItemId,
                            "truckId", truckId,
                            "driverId", driverId,
                            "source", "TRANSPORT",
                            "quantity", "20.000",
                            "plannedDate", today,
                            "distanceKm", "110.000"))))
        .andExpect(status().isCreated());

    // Attach an E-Way Bill expiring in 4 hours -> must trigger 24h alert!
    Instant soon = Instant.now().plus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    mvc.perform(
            put("/api/v1/trips/" + tripId + "/eway-bill")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("ewayBillNumber", "141008567890", "validUntil", soon.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eway_bill_number").value("141008567890"));

    mvc.perform(get("/api/v1/trips/eway-bill-alerts").param("hours", "24"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.eway_bill_number == '141008567890')]").isNotEmpty());
  }
}
