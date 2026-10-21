package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApplicationJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  String uid() {
    return UUID.randomUUID().toString();
  }

  Map<String, Object> customer(String username) {
    var body = new LinkedHashMap<String, Object>();
    body.put("applicationId", uid());
    body.put("role", "CUSTOMER");
    body.put("username", username);
    body.put("password", "customer-password-1");
    body.put("name", "Site Buyer " + username);
    body.put("mobile", "9876543210");
    body.put("address", "Plot 12, Kotputli");
    return body;
  }

  String postApplication(Map<String, Object> body, int status) throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/account-applications")
                    .with(anonymous())
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return status >= 400 || response.isBlank() ? "" : JsonPath.read(response, "$.id");
  }

  String branch() throws Exception {
    String code = "B" + uid().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    String response =
        mvc.perform(
                post("/api/v1/branches")
                    .with(user("admin").roles("SUPER_ADMIN"))
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code",
                                code,
                                "name",
                                "North yard",
                                "address",
                                "Industrial area",
                                "city",
                                "Gurugram",
                                "state",
                                "Haryana",
                                "pincode",
                                "122001"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.id");
  }

  void decide(String id, String action, Map<String, Object> body, String role) throws Exception {
    mvc.perform(
            post("/api/v1/account-applications/" + id + "/" + action)
                .with(user("ops").roles(role))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk());
  }

  @Test
  void approvedCustomerSeesOnlyTheirOwnRecord() throws Exception {
    var first = customer("buyer." + uid().substring(0, 8));
    var second = customer("buyer." + uid().substring(0, 8));
    String firstId = postApplication(first, 201);
    postApplication(first, 200);
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("username", first.get("username").toString())
                .param("password", "customer-password-1"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/account-applications").with(user("ops").roles("OPERATIONS")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + firstId + "')].status").value("PENDING"));
    mvc.perform(get("/api/v1/account-applications").with(user("accounts").roles("ACCOUNTS")))
        .andExpect(status().isForbidden());
    decide(firstId, "approve", Map.of("eventId", uid(), "reason", "Details match the enquiry"), "OPERATIONS");
    String secondId = postApplication(second, 201);
    decide(
        secondId, "approve", Map.of("eventId", uid(), "reason", "Second buyer confirmed"), "OPERATIONS");
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("username", first.get("username").toString())
                .param("password", "customer-password-1"))
        .andExpect(status().isNoContent());
    String portal =
        mvc.perform(
                get("/api/v1/portal")
                    .with(user(first.get("username").toString()).roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String own = JsonPath.read(portal, "$.record_id");
    assertThat(portal).doesNotContain(String.valueOf(second.get("name")));
    assertThat(JsonPath.<String>read(portal, "$.profile.name"))
        .isEqualTo(first.get("name"));
    assertThat(JsonPath.<String>read(portal, "$.profile.address")).isEqualTo("Plot 12, Kotputli");
    mvc.perform(get("/api/v1/customers").with(user(first.get("username").toString()).roles("CUSTOMER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/portal").with(user("ops").roles("OPERATIONS")))
        .andExpect(status().isForbidden());
    assertThat(
            db.queryForObject(
                "select count(*) from account_ownership where record_id=?",
                Integer.class,
                UUID.fromString(own)))
        .isEqualTo(1);
  }

  @Test
  void rejectionFreesTheUsernameAndDriverNeedsABranch() throws Exception {
    String username = "driver." + uid().substring(0, 8);
    var body = new LinkedHashMap<String, Object>();
    body.put("applicationId", uid());
    body.put("role", "DRIVER");
    body.put("username", username);
    body.put("password", "driver-password-12");
    body.put("name", "Ram Singh");
    body.put("mobile", "9876543211");
    body.put("licenseNumber", "DL" + uid().replace("-", "").substring(0, 10));
    body.put("licenseExpiry", LocalDate.now().plusYears(1).toString());
    body.put("emergencyContact", "Site office");
    body.put("emergencyMobile", "9876543212");
    String id = postApplication(body, 201);
    mvc.perform(
            post("/api/v1/account-applications/" + id + "/approve")
                .with(user("ops").roles("OPERATIONS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("eventId", uid(), "reason", "Licence checked, branch still missing"))))
        .andExpect(status().isBadRequest());
    decide(id, "reject", Map.of("eventId", uid(), "reason", "Licence copy is unreadable"), "OPERATIONS");
    body.put("applicationId", uid());
    String again = postApplication(body, 201);
    String branch = branch();
    decide(
        again,
        "approve",
        Map.of("eventId", uid(), "reason", "Licence and branch confirmed", "branchId", branch),
        "SUPER_ADMIN");
    String portal =
        mvc.perform(get("/api/v1/portal").with(user(username).roles("DRIVER")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<String>read(portal, "$.profile.license_number"))
        .isEqualTo(body.get("licenseNumber").toString().toUpperCase(Locale.ROOT));
    assertThat(JsonPath.<String>read(portal, "$.profile.branch_name")).isEqualTo("North yard");
  }

  @Test
  void supplierApplicationOpensTheirOwnPlant() throws Exception {
    var missing = new LinkedHashMap<String, Object>();
    missing.put("applicationId", uid());
    missing.put("role", "SUPPLIER");
    missing.put("username", "plant." + uid().substring(0, 8));
    missing.put("password", "supplier-password-1");
    missing.put("name", "Crusher Co");
    missing.put("mobile", "9876543213");
    postApplication(missing, 400);
    String gstin =
        "06ABCDE"
            + String.format("%04d", Math.floorMod(UUID.randomUUID().hashCode(), 10000))
            + "F1Z5";
    missing.put("gstin", gstin);
    missing.put("address", "Crusher road, Gurugram");
    missing.put("applicationId", uid());
    String id = postApplication(missing, 201);
    decide(id, "approve", Map.of("eventId", uid(), "reason", "GSTIN and address checked"), "OPERATIONS");
    String portal =
        mvc.perform(
                get("/api/v1/portal").with(user(missing.get("username").toString()).roles("SUPPLIER")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<?>>read(portal, "$.plants")).hasSize(1);
    assertThat(JsonPath.<String>read(portal, "$.profile.gstin")).isEqualTo(gstin);
  }
}
