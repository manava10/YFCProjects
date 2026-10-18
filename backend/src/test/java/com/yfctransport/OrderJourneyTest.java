package com.yfctransport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.regex.Pattern;
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
class OrderJourneyTest {
  @Autowired MockMvc mvc;

  String id(String body) {
    var m = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(body);
    if (!m.find()) throw new AssertionError(body);
    return m.group(1);
  }

  @Test
  @WithMockUser(roles = "SUPER_ADMIN")
  void orderedQuantityRemainsBillableDespiteShortage() throws Exception {
    String dispatch =
        """
        {"reference":"%s","loadedQuantity":99,"receivedQuantity":98}
        """
            .formatted("SLIP-" + java.util.UUID.randomUUID().toString().substring(0, 20));
    String customer =
        id(
            mvc.perform(
                    post("/api/v1/customers")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Retail buyer\",\"mobile\":\"9876543210\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String order =
        id(
            mvc.perform(
                    post("/api/v1/orders")
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            "{\"customerId\":\""
                                + customer
                                + "\",\"businessType\":\"MATERIAL_AND_TRANSPORT\",\"material\":\"20"
                                + " MM\",\"deliverySite\":\"Gurgaon Site"
                                + " A\",\"orderedQuantity\":100,\"unitRate\":1200}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(
            post("/api/v1/orders/" + order + "/dispatches")
                .with(csrf())
                .contentType("application/json")
                .content(dispatch))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billableQuantity").value(100))
        .andExpect(jsonPath("$.loadedQuantity").value(99))
        .andExpect(jsonPath("$.receivedQuantity").value(98))
        .andExpect(jsonPath("$.subtotal").value(120000));
    mvc.perform(
            post("/api/v1/orders/" + order + "/dispatches")
                .with(csrf())
                .contentType("application/json")
                .content(dispatch))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/orders/" + order))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loadedQuantity").value(99))
        .andExpect(jsonPath("$.billableQuantity").value(100));
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  void externalCustomerCannotAccessInternalRecords() throws Exception {
    mvc.perform(get("/api/v1/customers")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "SUPER_ADMIN")
  void writesRequireCsrf() throws Exception {
    mvc.perform(post("/api/v1/customers").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousApiRequestsAreRejected() throws Exception {
    mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "SUPER_ADMIN")
  void unknownCustomerCannotBeUsedInAnOrder() throws Exception {
    mvc.perform(
            post("/api/v1/orders")
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {"customerId":"00000000-0000-0000-0000-000000000001","businessType":"MATERIAL","material":"20 MM","deliverySite":"Site","orderedQuantity":100,"unitRate":1200}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void healthIsPublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
