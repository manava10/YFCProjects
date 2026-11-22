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
@WithMockUser(username = "credit-admin", roles = "SUPER_ADMIN")
class CreditLimitJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
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

  void setCreditLimit(String customerId, String limit) throws Exception {
    String accountId = uid();
    sales.create(
        "/api/v1/accounting/accounts",
        Map.of(
            "accountId",
            accountId,
            "code",
            "AR" + uid().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT),
            "name",
            "Customer AR",
            "type",
            "ASSET"));
    sales.create(
        "/api/v1/accounting/parties",
        Map.of(
            "profileId",
            uid(),
            "partyType",
            "CUSTOMER",
            "partyId",
            customerId,
            "accountId",
            accountId,
            "paymentTermsDays",
            30,
            "creditLimit",
            new BigDecimal(limit)));
  }

  @Test
  void blocksOrderConversionAboveCreditLimitAndAllowsSuperAdminOverrideWithAudit() throws Exception {
    var f = sales.fixture();
    // Set customer credit limit to 50,000 INR
    setCreditLimit(f.customer(), "50000.00");

    // Create RFQ + Quotation totalling 132,000 INR (exceeds 50,000 limit)
    var rfq = sales.create("/api/v1/rfqs", sales.rfqBody(f, "MATERIAL_AND_TRANSPORT", "100", "10"));
    var quote =
        sales.create(
            "/api/v1/rfqs/" + sales.id(rfq) + "/quotations",
            sales.quoteBody(rfq, "1000", "200", "1000", "200"));
    sales.send(quote);
    sales.accept(quote);
    String quoteId = sales.id(quote);

    // Standard conversion blocked with 409 Conflict
    mvc.perform(
            post("/api/v1/quotations/" + quoteId + "/convert")
                .with(user("ops-user").roles("OPERATIONS"))
                .with(csrf()))
        .andExpect(status().isConflict());

    // OPERATIONS cannot override credit limit (403 Forbidden)
    mvc.perform(
            post("/api/v1/quotations/" + quoteId + "/convert")
                .param("overrideCreditLimit", "true")
                .with(user("ops-user").roles("OPERATIONS"))
                .with(csrf()))
        .andExpect(status().isForbidden());

    // SUPER_ADMIN override succeeds and writes an audit event
    mvc.perform(
            post("/api/v1/quotations/" + quoteId + "/convert")
                .param("overrideCreditLimit", "true")
                .with(user("credit-admin").roles("SUPER_ADMIN"))
                .with(csrf()))
        .andExpect(status().isCreated());

    Integer overrideAudits =
        db.queryForObject(
            "select count(*) from audit_events where action='CREDIT_LIMIT_OVERRIDDEN' and"
                + " target_id=?",
            Integer.class,
            UUID.fromString(f.customer()));
    assertThat(overrideAudits).isEqualTo(1);
  }
}
