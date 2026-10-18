package com.yfctransport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FuelJourneyTest {
  @Autowired MockMvc mvc;

  String create(String path, String json) throws Exception {
    return JsonPath.read(
        mvc.perform(post(path).with(csrf()).contentType("application/json").content(json))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.id");
  }

  @Test
  @WithMockUser(roles = "SUPER_ADMIN")
  void priceHistoryAndReceiptCostAreIndependent() throws Exception {
    String truck =
        create(
            "/api/v1/trucks",
            "{\"registration\":\"HR"
                + java.util
                    .UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase()
                + "\",\"capacityTonnes\":35,\"tankCapacityLitres\":400,\"mileageKmPerLitre\":3}");
    String pump = java.util.UUID.randomUUID().toString();
    String rate =
        create(
            "/api/v1/fuel/rates",
            "{\"pump\":\""
                + pump
                + " Pump"
                + " A\",\"location\":\"Gurgaon\",\"effectiveDate\":\"2026-01-01\",\"pricePerLitre\":91.50}");
    String fuel =
        create(
            "/api/v1/fuel/entries",
            "{\"truckId\":\""
                + truck
                + "\",\"rateId\":\""
                + rate
                + "\",\"receiptReference\":\""
                + java.util.UUID.randomUUID()
                + "\",\"filledOn\":\"2026-01-01\",\"quantityLitres\":100,\"actualPricePerLitre\":92.00,\"priceVarianceReason\":\"Receipt"
                + " pump price differs\"}");
    create(
        "/api/v1/fuel/rates",
        "{\"pump\":\""
            + pump
            + " Pump"
            + " A\",\"location\":\"Gurgaon\",\"effectiveDate\":\"2026-01-02\",\"pricePerLitre\":95}");
    mvc.perform(get("/api/v1/fuel/entries/" + fuel))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(9200))
        .andExpect(jsonPath("$.actualPricePerLitre").value(92))
        .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    mvc.perform(get("/api/v1/fuel/rates/" + rate))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pricePerLitre").value(91.50));
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  void fuelIsHiddenFromCustomers() throws Exception {
    mvc.perform(get("/api/v1/fuel/rates/00000000-0000-0000-0000-000000000001"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OPERATIONS")
  void onlyAdminChangesDieselRates() throws Exception {
    mvc.perform(
            post("/api/v1/fuel/rates")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"pump\":\"Other\",\"location\":\"Delhi\",\"effectiveDate\":\"2026-01-01\",\"pricePerLitre\":91}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "SUPER_ADMIN")
  void invalidCustomerIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/customers")
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"\",\"mobile\":\"1\"}"))
        .andExpect(status().isBadRequest());
  }
}
