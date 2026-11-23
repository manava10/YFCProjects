package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "p2-admin", roles = "SUPER_ADMIN")
class Phase2EnterpriseOpsJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String uid() {
    return UUID.randomUUID().toString();
  }

  private String code(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
  }

  @Test
  void maintenanceDriverMobileAlertsAndGstCreditNotesWorkEndToEnd() throws Exception {
    String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();

    // 1. Setup Rajasthan Branch (GSTIN 08), Haryana Customer (GSTIN 06), Project, Site, Material
    String brJson =
        mvc.perform(
                post("/api/v1/branches")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code", code("BR"),
                                "name", "Behror Fleet Hub",
                                "address", "NH-48 Behror",
                                "city", "Behror",
                                "state", "Rajasthan",
                                "pincode", "301701",
                                "gstin", "08ABCDE1234F1Z5",
                                "bankName", "ICICI Bank",
                                "bankAccount", "001122334455",
                                "bankIfsc", "ICIC0000123"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String branchId = JsonPath.read(brJson, "$.id");

    String custJson =
        mvc.perform(
                post("/api/v1/customers")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "name", "NHAI Infra " + code("C"),
                                "mobile", "9810022334",
                                "gstin", "06XYZAB9876C1Z2"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String customerId = JsonPath.read(custJson, "$.id");

    String projJson =
        mvc.perform(
                post("/api/v1/customers/" + customerId + "/projects")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "branchId", branchId,
                                "code", code("PR"),
                                "name", "Highway Widening",
                                "description", "Pkg 4"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String projId = JsonPath.read(projJson, "$.id");

    String siteJson =
        mvc.perform(
                post("/api/v1/customers/" + customerId + "/projects/" + projId + "/sites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code", code("ST"),
                                "name", "Dharuhera Yard",
                                "address", "NH-48 Dharuhera",
                                "city", "Rewari",
                                "state", "Haryana",
                                "pincode", "123106"))))
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
                            Map.of("code", code("MT"), "name", "10mm Aggregate", "category", "AGGREGATES"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String matId = JsonPath.read(matJson, "$.id");

    // 2. Register Owned Truck + Activate Compliance + Record Preventive Tyre Maintenance
    String truckReg = "RJ32" + code("TK").substring(0, 6);
    String truckJson =
        mvc.perform(
                post("/api/v1/trucks")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "registration", truckReg,
                                "capacityTonnes", "35.000",
                                "tankCapacityLitres", "320.000",
                                "mileageKmPerLitre", "3.800"))))
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
                            "branchId", branchId,
                            "ownerName", "YFC Transport",
                            "truckType", "12-WHEELER TIPPER",
                            "rcNumber", "RC-" + code("R"),
                            "insuranceNumber", "IN-" + code("I"),
                            "insuranceExpiry", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(12).toString(),
                            "fitnessExpiry", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1).toString(),
                            "permitExpiry", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(1).toString(),
                            "pollutionExpiry", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusMonths(6).toString(),
                            "status", "ACTIVE"))))
        .andExpect(status().isOk());

    // Record Tyre & Service Maintenance Log with upcoming due date + odometer
    mvc.perform(
            post("/api/v1/fleet/maintenance")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "truckId", truckId,
                            "category", "TYRE",
                            "serviceDate", today,
                            "odometerKm", 62000,
                            "nextDueDate", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(10).toString(),
                            "nextDueOdometerKm", 62500,
                            "vendorName", "Apollo Tyre Care Kotputli",
                            "invoiceReference", "ATC-909",
                            "costInr", "48000.00",
                            "notes", "Rear axle radial tyre pair replacement"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.current_odometer_km").value(62000))
        .andExpect(jsonPath("$.cost_inr").value(48000.00));

    mvc.perform(get("/api/v1/fleet/maintenance-due").param("days", "30").param("kmMargin", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.truck_registration == '" + truckReg + "')]").isNotEmpty());

    // Verify Truck Profitability Report includes the ₹48,000 maintenance cost!
    mvc.perform(
            get("/api/v1/accounting/reports/truck-profitability")
                .param("from", today)
                .param("to", today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trucks[?(@.registration == '" + truckReg + "')].maintenanceCost").value(48000.00));

    // 3. Open Two Driver Portal Accounts (Driver 1 assigned to trip, Driver 2 unauthorized)
    String d1User = "drv1_" + code("D").toLowerCase();
    String d2User = "drv2_" + code("D").toLowerCase();
    String d1App =
        mvc.perform(
                post("/api/v1/account-applications")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "applicationId", uid(),
                                "role", "DRIVER",
                                "username", d1User,
                                "password", "DriverPass!2026",
                                "name", "Rajesh Driver",
                                "mobile", "9812345670",
                                "licenseNumber", "RJ32-" + code("DL"),
                                "licenseExpiry", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(2).toString(),
                                "emergencyContact", "Karan",
                                "emergencyMobile", "9812345671"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String d1Approved =
        mvc.perform(
                post("/api/v1/account-applications/" + JsonPath.read(d1App, "$.id") + "/approve")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "eventId", uid(),
                                "branchId", branchId,
                                "reason", "Verified DL"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String driver1Id = JsonPath.read(d1Approved, "$.record_id");

    String d2App =
        mvc.perform(
                post("/api/v1/account-applications")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "applicationId", uid(),
                                "role", "DRIVER",
                                "username", d2User,
                                "password", "DriverPass!2026",
                                "name", "Other Driver",
                                "mobile", "9812345699",
                                "licenseNumber", "RJ32-" + code("DL"),
                                "licenseExpiry", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusYears(2).toString(),
                                "emergencyContact", "Vijay",
                                "emergencyMobile", "9812345698"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            post("/api/v1/account-applications/" + JsonPath.read(d2App, "$.id") + "/approve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "eventId", uid(),
                            "branchId", branchId,
                            "reason", "Verified DL"))))
        .andExpect(status().isOk());

    // 4. Create Transport Order -> Allocate Trip to Driver 1 -> Attach Expiring E-Way Bill
    String rfq =
        mvc.perform(
                post("/api/v1/rfqs")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "customerId", customerId,
                                "deliverySiteId", siteId,
                                "requiredDate", today,
                                "businessType", "TRANSPORT",
                                "items", List.of(Map.of("materialId", matId, "quantity", "20.000"))))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String quote =
        mvc.perform(
                post("/api/v1/rfqs/" + JsonPath.read(rfq, "$.id") + "/quotations")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "validUntil", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5).toString(),
                                "paymentTerms", "Immediate",
                                "deliveryTerms", "Tipper",
                                "items",
                                List.of(
                                    Map.of(
                                        "rfqItemId", JsonPath.read(rfq, "$.items[0].id"),
                                        "materialRate", "0.00",
                                        "transportRate", "500.00"))))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String quoteId = JsonPath.read(quote, "$.id");
    mvc.perform(post("/api/v1/quotations/" + quoteId + "/send").with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/quotations/" + quoteId + "/accept")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("customerContact", "Amit", "approvalReference", "PO-P2-1"))))
        .andExpect(status().isOk());
    String conv =
        mvc.perform(post("/api/v1/quotations/" + quoteId + "/convert").with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String orderId = JsonPath.read(conv, "$.orderId");
    String orderJson =
        mvc.perform(get("/api/v1/orders/" + orderId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String orderItemId = JsonPath.read(orderJson, "$.items[0].id");

    String tripId = uid();
    mvc.perform(
            post("/api/v1/trips")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "tripId", tripId,
                            "orderItemId", orderItemId,
                            "truckId", truckId,
                            "driverId", driver1Id,
                            "source", "TRANSPORT",
                            "quantity", "20.000",
                            "plannedDate", today,
                            "distanceKm", "80.000"))))
        .andExpect(status().isCreated());
    mvc.perform(
            put("/api/v1/trips/" + tripId + "/eway-bill")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "ewayBillNumber", "191008554433",
                            "validUntil",
                                Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString()))))
        .andExpect(status().isOk());

    // 5. Automated Alerts Summary & Manual Digest Run (must capture E-Way Bill + Truck Compliance + Maintenance!)
    mvc.perform(get("/api/v1/alerts/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ewayBillAlerts[?(@.eway_bill_number == '191008554433')]").isNotEmpty())
        .andExpect(jsonPath("$.truckComplianceAlerts[?(@.registration == '" + truckReg + "')]").isNotEmpty())
        .andExpect(jsonPath("$.maintenanceDueAlerts[?(@.truck_registration == '" + truckReg + "')]").isNotEmpty());

    mvc.perform(post("/api/v1/alerts/digest/run").with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.dispatched_channel").value("IN_APP"))
        .andExpect(jsonPath("$.dispatch_status").value("RECORDED"));

    mvc.perform(get("/api/v1/alerts/digests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 6. Driver Mobile Trip Execution + POD Upload + Cross-Driver IDOR Check!
    // Driver 2 MUST get 404 on Driver 1's trip
    mvc.perform(
            post("/api/v1/portal/driver/trips/" + tripId + "/start-loading")
                .with(user(d2User).roles("DRIVER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid()))))
        .andExpect(status().isNotFound());

    // Driver 1 executes full trip from mobile portal
    mvc.perform(
            post("/api/v1/portal/driver/trips/" + tripId + "/start-loading")
                .with(user(d1User).roles("DRIVER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOADING"));

    mvc.perform(
            post("/api/v1/portal/driver/trips/" + tripId + "/load")
                .with(user(d1User).roles("DRIVER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "eventId", uid(),
                            "gross", "34.000",
                            "tare", "14.000",
                            "slipReference", "WB-DRV-101"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOADED"));

    // Driver uploads weighbridge slip PDF directly from mobile portal
    byte[] pdf = "%PDF-1.4\n%driver-weighbridge-slip\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    mvc.perform(
            multipart("/api/v1/portal/driver/trips/" + tripId + "/documents")
                .file(new MockMultipartFile("file", "wb-slip.pdf", "application/pdf", pdf))
                .param("documentId", uid())
                .with(user(d1User).roles("DRIVER"))
                .with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.filename").value("wb-slip.pdf"));

    mvc.perform(
            get("/api/v1/portal/driver/trips/" + tripId + "/documents")
                .with(user(d1User).roles("DRIVER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mvc.perform(
            post("/api/v1/portal/driver/trips/" + tripId + "/dispatch")
                .with(user(d1User).roles("DRIVER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISPATCHED"));

    mvc.perform(
            post("/api/v1/portal/driver/trips/" + tripId + "/arrive")
                .with(user(d1User).roles("DRIVER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARRIVED"));

    mvc.perform(
            post("/api/v1/portal/driver/trips/" + tripId + "/start-unloading")
                .with(user(d1User).roles("DRIVER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNLOADING"));

    mvc.perform(
            post("/api/v1/portal/driver/trips/" + tripId + "/deliver")
                .with(user(d1User).roles("DRIVER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "eventId", uid(),
                            "gross", "34.000",
                            "tare", "14.000",
                            "acceptedQuantity", "20.000",
                            "slipReference", "DEL-SLIP-101",
                            "receiverName", "Site Engineer Verma",
                            "proofReference", "POD-PHOTO-101"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"));

    // 7. GST-Compliant Credit Note (Section 34 CGST) + Maker-Checker + GSTR-1 Table 9B
    String prepId = uid();
    mvc.perform(
            post("/api/v1/accounting/billing-preparations")
                .with(user("p2-maker").roles("ACCOUNTS"))
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
                            "reference", "P2-INV-1",
                            "evidenceReference", "WB-DRV-101",
                            "basisReason", "Ordered basis"))))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/accounting/billing-preparations/" + prepId + "/ready")
                .with(user("p2-checker").roles("ACCOUNTS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid(), "reason", "Verified"))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/accounting/billing-preparations/" + prepId + "/tax")
                .with(user("p2-checker").roles("ACCOUNTS"))
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
                                    "component", "TRANSPORT",
                                    "hsnOrSac", "996511",
                                    "ratePercent", "5.00"))))))
        .andExpect(status().isCreated());

    // Maker creates Credit Note (₹1,000 taxable @ 5% IGST = ₹1,050)
    String noteJson =
        mvc.perform(
                post("/api/v1/accounting/gst-notes")
                    .with(user("p2-maker").roles("ACCOUNTS"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "originalPreparationId", prepId,
                                "noteType", "CREDIT_NOTE",
                                "noteDate", today,
                                "reasonCode", "WEIGHT_SHORTAGE",
                                "reasonDetail", "2 tonne weighbridge tolerance adjustment",
                                "hsnOrSac", "996511",
                                "taxableAmount", "1000.00",
                                "gstRatePercent", "5.00"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.igst_amount").value(50.00))
            .andExpect(jsonPath("$.total_amount").value(1050.00))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String noteId = JsonPath.read(noteJson, "$.id");
    String noteNumber = JsonPath.read(noteJson, "$.note_number");
    assertThat(noteNumber).matches("CN-\\d{4}-08-\\d{5}");

    // Same maker CANNOT approve their own Credit Note (Maker-Checker enforcement!)
    mvc.perform(
            post("/api/v1/accounting/gst-notes/" + noteId + "/approve")
                .with(user("p2-maker").roles("ACCOUNTS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "Self approve attempt"))))
        .andExpect(status().isConflict());

    // Checker approves Credit Note
    mvc.perform(
            post("/api/v1/accounting/gst-notes/" + noteId + "/approve")
                .with(user("p2-checker").roles("ACCOUNTS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "Verified against weighbridge slip"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // GSTR-1 Table 9B must include approved Credit Note (CDNR) and net down total!
    mvc.perform(
            get("/api/v1/accounting/reports/gstr1")
                .param("from", today)
                .param("to", today)
                .param("branchId", branchId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.creditDebitNotes[?(@.note_number == '" + noteNumber + "')].category").value("CDNR"))
        .andExpect(jsonPath("$.totalPayable").value(10500.00))
        .andExpect(jsonPath("$.netPayable").value(9450.00));
  }
}
