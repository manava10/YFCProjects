package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase1BHardeningJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  private String uid() {
    return UUID.randomUUID().toString();
  }

  private String code(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
  }

  @Test
  void persistentSessionSurvivesRestartAndRevokesOnLogout() throws Exception {
    String username = "persist_" + code("U").toLowerCase();
    String password = "PersistPassword!2026";

    // Open a real customer account via account application approval (works standalone in any order)
    String appJson =
        mvc.perform(
                post("/api/v1/account-applications")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "applicationId", uid(),
                                "role", "CUSTOMER",
                                "username", username,
                                "password", password,
                                "name", "Persistent Customer",
                                "mobile", "9810011223",
                                "gstin", "08ABCDE1234F1Z5"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String appId = JsonPath.read(appJson, "$.id");
    mvc.perform(
            post("/api/v1/account-applications/" + appId + "/approve")
                .with(user("p1b-admin").roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid(), "reason", "Approved"))))
        .andExpect(status().isOk());

    // Log in via POST /login (Customer portal) -> must issue YFC_SESSION cookie + rate-limit headers
    MvcResult loginResult =
        mvc.perform(
                post("/login")
                    .with(csrf())
                    .param("username", username)
                    .param("password", password)
                    .param("portal", "CUSTOMER"))
            .andExpect(status().isNoContent())
            .andExpect(header().exists("X-RateLimit-Limit"))
            .andReturn();

    String setCookie =
        loginResult.getResponse().getHeaders("Set-Cookie").stream()
            .filter(h -> h.startsWith("YFC_SESSION="))
            .findFirst()
            .orElseThrow();
    String token = setCookie.substring("YFC_SESSION=".length()).split(";")[0];
    assertThat(token).isNotBlank();

    // Simulate full backend restart (brand-new MockMvc request with NO JSESSIONID, ONLY YFC_SESSION!)
    mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("YFC_SESSION", token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"));

    // Logout using only the persistent session cookie -> must revoke DB row
    mvc.perform(post("/logout").with(csrf()).cookie(new Cookie("YFC_SESSION", token)))
        .andExpect(status().isNoContent());

    // Replaying the revoked YFC_SESSION cookie must now fail with 401
    mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("YFC_SESSION", token)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "p1b-admin", roles = "SUPER_ADMIN")
  void serverSideFiltersAndPortalInvoiceChallanWithIdorProtectionWork() throws Exception {
    String today = LocalDate.now().toString();

    // 1. Create Rajasthan Branch with GSTIN + Bank
    String brJson =
        mvc.perform(
                post("/api/v1/branches")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code", code("BR"),
                                "name", "Kotputli Hub " + code("K"),
                                "address", "NH-48",
                                "city", "Kotputli",
                                "state", "Rajasthan",
                                "pincode", "303108",
                                "gstin", "08ABCDE1234F1Z5",
                                "bankName", "HDFC Bank",
                                "bankAccount", "502000991122",
                                "bankIfsc", "HDFC0001234"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String branchId = JsonPath.read(brJson, "$.id");

    // 2. Create two distinct customers via Account Application approval so we have real Portal logins!
    String c1User = "cust1_" + code("A").toLowerCase();
    String c2User = "cust2_" + code("B").toLowerCase();
    String c1Tag = code("ALPHA");
    String c2Tag = code("OMEGA");

    String app1 =
        mvc.perform(
                post("/api/v1/account-applications")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "applicationId", uid(),
                                "role", "CUSTOMER",
                                "username", c1User,
                                "password", "CustomerPass!2026",
                                "name", "Infra " + c1Tag,
                                "mobile", "9810001122",
                                "gstin", "06XYZAB9876C1Z2",
                                "address", "Sector 18 Gurugram"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String app1Id = JsonPath.read(app1, "$.id");
    String approved1 =
        mvc.perform(
                post("/api/v1/account-applications/" + app1Id + "/approve")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("eventId", uid(), "reason", "Verified"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String customer1Id = JsonPath.read(approved1, "$.record_id");

    String app2 =
        mvc.perform(
                post("/api/v1/account-applications")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "applicationId", uid(),
                                "role", "CUSTOMER",
                                "username", c2User,
                                "password", "CustomerPass!2026",
                                "name", "Builders " + c2Tag,
                                "mobile", "9810009988",
                                "gstin", "08PQRST5678L1Z9",
                                "address", "Jaipur Road"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String app2Id = JsonPath.read(app2, "$.id");
    mvc.perform(
            post("/api/v1/account-applications/" + app2Id + "/approve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid(), "reason", "Verified"))))
        .andExpect(status().isOk());

    // Test server-side search on /api/v1/customers?q=
    mvc.perform(get("/api/v1/customers").param("q", c1Tag))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(customer1Id));

    // 3. Create Project, Site, Material, Order, Bill + Tax Invoice, Trip + E-Way Bill for Customer 1
    String projJson =
        mvc.perform(
                post("/api/v1/customers/" + customer1Id + "/projects")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "branchId", branchId,
                                "code", code("PR"),
                                "name", "Expressway Pkg",
                                "description", "NH"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String projId = JsonPath.read(projJson, "$.id");

    String siteJson =
        mvc.perform(
                post("/api/v1/customers/" + customer1Id + "/projects/" + projId + "/sites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code", code("ST"),
                                "name", "Manesar Site " + c1Tag,
                                "address", "IMT Manesar Sector 8",
                                "city", "Gurugram",
                                "state", "Haryana",
                                "pincode", "122051"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String siteId = JsonPath.read(siteJson, "$.id");

    String matName = "20mm Grit " + c1Tag;
    String matJson =
        mvc.perform(
                post("/api/v1/materials")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("code", code("MT"), "name", matName, "category", "AGGREGATES"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String matId = JsonPath.read(matJson, "$.id");

    // Create Transport order via RFQ -> Quotation -> Convert
    String rfq =
        mvc.perform(
                post("/api/v1/rfqs")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "customerId", customer1Id,
                                "deliverySiteId", siteId,
                                "requiredDate", LocalDate.now().plusDays(1).toString(),
                                "businessType", "TRANSPORT",
                                "items", List.of(Map.of("materialId", matId, "quantity", "25.000"))))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String rfqId = JsonPath.read(rfq, "$.id");
    String rfqItemId = JsonPath.read(rfq, "$.items[0].id");

    String quote =
        mvc.perform(
                post("/api/v1/rfqs/" + rfqId + "/quotations")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "validUntil", LocalDate.now().plusDays(5).toString(),
                                "paymentTerms", "15 days",
                                "deliveryTerms", "Tipper",
                                "items",
                                List.of(
                                    Map.of(
                                        "rfqItemId", rfqItemId,
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
                        Map.of("customerContact", "Rohan", "approvalReference", "PO-99"))))
        .andExpect(status().isOk());
    String orderConv =
        mvc.perform(post("/api/v1/quotations/" + quoteId + "/convert").with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String orderId = JsonPath.read(orderConv, "$.orderId");
    String orderDetail =
        mvc.perform(get("/api/v1/orders/" + orderId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String orderItemId = JsonPath.read(orderDetail, "$.items[0].id");

    // Test server-side search & branch filter on /api/v1/orders
    mvc.perform(
            get("/api/v1/orders")
                .param("q", c1Tag)
                .param("branchId", branchId)
                .param("businessType", "TRANSPORT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(orderId));

    // Create Billing Prep -> Ready -> Assess IGST
    String prepId = uid();
    mvc.perform(
            post("/api/v1/accounting/billing-preparations")
                .with(user("p1b-maker").roles("ACCOUNTS"))
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
                            "reference", "P1B-" + c1Tag,
                            "evidenceReference", "WB-P1B",
                            "basisReason", "Ordered basis"))))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/accounting/billing-preparations/" + prepId + "/ready")
                .with(user("p1b-checker").roles("ACCOUNTS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", uid(), "reason", "Approved"))))
        .andExpect(status().isOk());
    String taxRes =
        mvc.perform(
                post("/api/v1/accounting/billing-preparations/" + prepId + "/tax")
                    .with(user("p1b-checker").roles("ACCOUNTS"))
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
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String invoiceNumber = JsonPath.read(taxRes, "$.invoice_number");

    // Test server-side search & status filter on /api/v1/accounting/billing-preparations
    mvc.perform(
            get("/api/v1/accounting/billing-preparations")
                .param("q", invoiceNumber)
                .param("status", "READY")
                .param("branchId", branchId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(prepId));

    // Create Truck + Driver + Trip + E-Way Bill
    String truckReg = "RJ14" + code("PK").substring(0, 6);
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
                            "branchId", branchId,
                            "ownerName", "YFC Transport",
                            "truckType", "TIPPER",
                            "rcNumber", "RC-" + code("P"),
                            "insuranceNumber", "IN-" + code("P"),
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
                                "branchId", branchId,
                                "name", "Vikram " + c1Tag,
                                "mobile", "9811199900",
                                "licenseNumber", "RJ14-" + code("DR"),
                                "licenseExpiry", LocalDate.now().plusYears(2).toString(),
                                "emergencyContact", "Mohan",
                                "emergencyMobile", "9811199911",
                                "active", true))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String driverId = JsonPath.read(driverJson, "$.id");

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
                            "driverId", driverId,
                            "source", "TRANSPORT",
                            "quantity", "25.000",
                            "plannedDate", today,
                            "distanceKm", "95.000"))))
        .andExpect(status().isCreated());
    String ewbNo = "181009" + Math.abs(UUID.randomUUID().hashCode() % 900000 + 100000);
    mvc.perform(
            put("/api/v1/trips/" + tripId + "/eway-bill")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "ewayBillNumber", ewbNo,
                            "validUntil",
                                Instant.now().plus(12, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString()))))
        .andExpect(status().isOk());

    // Test server-side search & filters on /api/v1/trips (searching by E-Way Bill No!)
    mvc.perform(
            get("/api/v1/trips")
                .param("q", ewbNo)
                .param("status", "ALLOCATED")
                .param("source", "TRANSPORT")
                .param("branchId", branchId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(tripId));

    // Test server-side search on /api/v1/purchases
    mvc.perform(get("/api/v1/purchases").param("branchId", branchId).param("status", "OPEN"))
        .andExpect(status().isOk());

    // 4. Customer 1 accesses their own Printable Tax Invoice & Delivery Challan via /api/v1/portal/**
    mvc.perform(get("/api/v1/portal/bills/" + prepId).with(user(c1User).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoice_number").value(invoiceNumber))
        .andExpect(jsonPath("$.branch.gstin").value("08ABCDE1234F1Z5"))
        .andExpect(jsonPath("$.branch.bank_ifsc").value("HDFC0001234"))
        .andExpect(jsonPath("$.party_gstin").value("06XYZAB9876C1Z2"))
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.tax_lines[0].hsn_or_sac").value("996511"));

    mvc.perform(get("/api/v1/portal/trips/" + tripId).with(user(c1User).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eway_bill_number").value(ewbNo))
        .andExpect(jsonPath("$.truck_registration").value(truckReg))
        .andExpect(jsonPath("$.material").value(matName))
        .andExpect(jsonPath("$.branch.gstin").value("08ABCDE1234F1Z5"));

    // 5. IDOR check: Customer 2 MUST get 404 when trying to view Customer 1's invoice or challan!
    mvc.perform(get("/api/v1/portal/bills/" + prepId).with(user(c2User).roles("CUSTOMER")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/portal/trips/" + tripId).with(user(c2User).roles("CUSTOMER")))
        .andExpect(status().isNotFound());
  }
}
