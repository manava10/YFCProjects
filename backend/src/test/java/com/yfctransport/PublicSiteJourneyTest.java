package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicSiteJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void theCustomerPageFollowsTheCardOperationsPublishes() throws Exception {
    String site =
        mvc.perform(get("/api/v1/site").with(anonymous()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.heroTitle").value("Building India."))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Map<String, Object> material = named(site, "materials", "20 MM Aggregate");
    Map<String, Object> plant = named(site, "plants", "Kotputli Crusher Plant");
    Map<String, Object> drop = named(site, "destinations", "Gurugram, Haryana");
    assertThat(((Number) material.get("rate")).doubleValue()).isEqualTo(820);

    String quote =
        mvc.perform(
                post("/api/v1/site/quotes")
                    .with(anonymous())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "materialId",
                                material.get("id"),
                                "plantId",
                                plant.get("id"),
                                "destinationId",
                                drop.get("id"),
                                "quantity",
                                20,
                                "mediator",
                                "Rajpal"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.stage").value("CONFIRMED"))
            .andExpect(jsonPath("$.freightRate").value(420))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String reference = JsonPath.read(quote, "$.reference");

    mvc.perform(get("/api/v1/site/quotes/" + reference).with(anonymous()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stageLabel").value("Confirmed"))
        .andExpect(jsonPath("$.materialRate").value(820));

    String desk =
        mvc.perform(get("/api/v1/site/desk").with(user("ops").roles("OPERATIONS")).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String quoteId =
        ((List<Map<String, Object>>) JsonPath.<List<Map<String, Object>>>read(desk, "$.quotes"))
            .stream()
            .filter(row -> reference.equals(row.get("reference")))
            .map(row -> String.valueOf(row.get("id")))
            .findFirst()
            .orElseThrow();

    mvc.perform(
            post("/api/v1/site/desk/quotes/" + quoteId + "/stage")
                .with(anonymous())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"DELIVERED\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/site/desk/quotes/" + quoteId + "/stage")
                .with(user("books").roles("ACCOUNTS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"DELIVERED\"}"))
        .andExpect(status().isForbidden());

    Map<String, Object> edit = new LinkedHashMap<>(material);
    edit.put("rate", 900);
    edit.remove("id");
    mvc.perform(
            put("/api/v1/site/materials/" + material.get("id"))
                .with(user("ops").roles("OPERATIONS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rate").value(900));

    String published =
        mvc.perform(get("/api/v1/site").with(anonymous()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(((Number) named(published, "materials", "20 MM Aggregate").get("rate")).doubleValue())
        .isEqualTo(900);
    mvc.perform(get("/api/v1/site/quotes/" + reference).with(anonymous()))
        .andExpect(jsonPath("$.materialRate").value(820));

    mvc.perform(
            post("/api/v1/site/desk/quotes/" + quoteId + "/stage")
                .with(user("ops").roles("OPERATIONS"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"LOADING\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stageLabel").value("Loading"));
    mvc.perform(get("/api/v1/site/quotes/" + reference).with(anonymous()))
        .andExpect(jsonPath("$.stage").value("LOADING"));

    String enquiry =
        mvc.perform(
                post("/api/v1/site/enquiries")
                    .with(anonymous())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("name", "Site Buyer", "phone", "9876543210", "note", "Need 20 mm"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String enquiryRef = JsonPath.read(enquiry, "$.reference");
    mvc.perform(get("/api/v1/site/desk").with(user("ops").roles("OPERATIONS")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enquiries[?(@.reference == '" + enquiryRef + "')].name").value("Site Buyer"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> named(String body, String list, String name) {
    return ((List<Map<String, Object>>) JsonPath.<List<Map<String, Object>>>read(body, "$." + list))
        .stream()
        .filter(row -> name.equals(row.get("name")))
        .findFirst()
        .orElseThrow();
  }
}
