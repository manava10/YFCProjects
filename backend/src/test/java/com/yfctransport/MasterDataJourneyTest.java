package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "master-admin", roles = "SUPER_ADMIN")
class MasterDataJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  static final AtomicInteger GST_SEQUENCE = new AtomicInteger(1000);

  String code() {
    return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  String create(String path, String json) throws Exception {
    return JsonPath.read(
        mvc.perform(post(path).with(csrf()).contentType("application/json").content(json))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.id");
  }

  String branchJson(String code) {
    return """
    {"code":"%s","name":"YFC branch","address":"Office address","city":"Gurgaon","state":"Haryana","pincode":"122001"}
    """
        .formatted(code);
  }

  String branch() throws Exception {
    return create("/api/v1/branches", branchJson(code()));
  }

  String customer() throws Exception {
    return create("/api/v1/customers", "{\"name\":\"Buyer\",\"mobile\":\"9876543210\"}");
  }

  String materialJson(String code, String name) {
    return "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"category\":\"AGGREGATES\"}";
  }

  String material() throws Exception {
    return create("/api/v1/materials", materialJson(code(), "20 MM Aggregate"));
  }

  String projectPath(String customer) {
    return "/api/v1/customers/" + customer + "/projects";
  }

  String project(String customer, String branch) throws Exception {
    return create(
        projectPath(customer),
        "{\"branchId\":\""
            + branch
            + "\",\"code\":\""
            + code()
            + "\",\"name\":\"Construction Project\"}");
  }

  String sitesPath(String customer, String project) {
    return projectPath(customer) + "/" + project + "/sites";
  }

  String siteJson(String code, String address) {
    return "{\"code\":\"" + code + "\",\"name\":\"Delivery Site\",\"address\":\"" + address + "\"}";
  }

  String site(String customer, String project) throws Exception {
    return create(sitesPath(customer, project), siteJson(code(), "Original site address"));
  }

  String supplierJson(String code, String gst) {
    return "{\"code\":\""
        + code
        + "\",\"name\":\"Crusher supplier\",\"gstin\":\""
        + gst
        + "\",\"mobile\":\"9876543210\",\"address\":\"Supplier address\"}";
  }

  String gst() {
    StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < 5; i++)
      prefix.append((char) ('A' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
    return "06" + prefix + GST_SEQUENCE.incrementAndGet() + "F1Z5";
  }

  String supplier() throws Exception {
    return create("/api/v1/suppliers", supplierJson(code(), gst()));
  }

  String plant(String supplier) throws Exception {
    return create(
        "/api/v1/suppliers/" + supplier + "/plants",
        "{\"code\":\"" + code() + "\",\"name\":\"Crusher Plant\",\"address\":\"Plant address\"}");
  }

  String orderJson(String customer, String site, String material) {
    return "{\"customerId\":\""
        + customer
        + "\",\"businessType\":\"MATERIAL_AND_TRANSPORT\",\"deliverySiteId\":\""
        + site
        + "\",\"materialId\":\""
        + material
        + "\",\"orderedQuantity\":100,\"unitRate\":1200}";
  }

  void setActive(String path, boolean active) throws Exception {
    mvc.perform(
            patch(path + "/status")
                .with(csrf())
                .contentType("application/json")
                .content("{\"active\":" + active + "}"))
        .andExpect(status().isOk());
  }

  @Test
  void customerCanHaveMultipleProjectsAndSitesWithParentIsolation() throws Exception {
    String branch = branch(), customer = customer(), otherCustomer = customer();
    String first = project(customer, branch), second = project(customer, branch);
    String firstSite = site(customer, first);
    site(customer, first);
    site(customer, second);
    mvc.perform(get(projectPath(customer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(sitesPath(customer, first))).andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(projectPath(otherCustomer) + "/" + first)).andExpect(status().isNotFound());
    mvc.perform(get(sitesPath(customer, second) + "/" + firstSite))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/orders")
                .with(csrf())
                .contentType("application/json")
                .content(orderJson(otherCustomer, firstSite, material())))
        .andExpect(status().isNotFound());
  }

  @Test
  void normalizedOrdersPreserveSnapshotsWhenMasterRecordsChange() throws Exception {
    String branch = branch(),
        customer = customer(),
        project = project(customer, branch),
        site = site(customer, project),
        material = material();
    String order = create("/api/v1/orders", orderJson(customer, site, material));
    mvc.perform(
            put("/api/v1/materials/" + material)
                .with(csrf())
                .contentType("application/json")
                .content(materialJson(code(), "Renamed aggregate")))
        .andExpect(status().isOk());
    mvc.perform(
            put(sitesPath(customer, project) + "/" + site)
                .with(csrf())
                .contentType("application/json")
                .content(siteJson(code(), "New site address")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/orders/" + order))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.branchId").value(branch))
        .andExpect(jsonPath("$.projectId").value(project))
        .andExpect(jsonPath("$.deliverySiteId").value(site))
        .andExpect(jsonPath("$.materialId").value(material))
        .andExpect(jsonPath("$.material").value("20 MM Aggregate"))
        .andExpect(jsonPath("$.deliverySite").value("Original site address"))
        .andExpect(jsonPath("$.billableQuantity").value(100))
        .andExpect(jsonPath("$.subtotal").value(120000));
    String anotherCustomer = customer();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update sales_orders set customer_id=? where id=?",
                    UUID.fromString(anotherCustomer),
                    UUID.fromString(order)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void inactiveBranchPreventsNewProjectsAndOrdersButPreservesHistory() throws Exception {
    String branch = branch(),
        customer = customer(),
        project = project(customer, branch),
        site = site(customer, project),
        material = material();
    String order = create("/api/v1/orders", orderJson(customer, site, material));
    setActive("/api/v1/branches/" + branch, false);
    mvc.perform(get("/api/v1/orders/" + order)).andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/orders")
                .with(csrf())
                .contentType("application/json")
                .content(orderJson(customer, site, material)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(projectPath(customer))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"branchId\":\"" + branch + "\",\"code\":\"NEW\",\"name\":\"New project\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void inactiveSitesProjectsAndMaterialsCannotBeSelectedForNewOrders() throws Exception {
    String branch = branch(),
        customer = customer(),
        project = project(customer, branch),
        site = site(customer, project),
        material = material();
    String json = orderJson(customer, site, material);
    setActive(sitesPath(customer, project) + "/" + site, false);
    mvc.perform(post("/api/v1/orders").with(csrf()).contentType("application/json").content(json))
        .andExpect(status().isBadRequest());
    setActive(sitesPath(customer, project) + "/" + site, true);
    setActive(projectPath(customer) + "/" + project, false);
    mvc.perform(post("/api/v1/orders").with(csrf()).contentType("application/json").content(json))
        .andExpect(status().isBadRequest());
    setActive(projectPath(customer) + "/" + project, true);
    setActive("/api/v1/materials/" + material, false);
    mvc.perform(post("/api/v1/orders").with(csrf()).contentType("application/json").content(json))
        .andExpect(status().isBadRequest());
  }

  @Test
  void mismatchedBranchAndProjectAreRejectedInNormalizedOrders() throws Exception {
    String branch = branch(),
        otherBranch = branch(),
        customer = customer(),
        project = project(customer, branch),
        otherProject = project(customer, branch),
        site = site(customer, project),
        material = material();
    String json = orderJson(customer, site, material);
    mvc.perform(
            post("/api/v1/orders")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.substring(0, json.length() - 1)
                        + ",\"branchId\":\""
                        + otherBranch
                        + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/orders")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.substring(0, json.length() - 1)
                        + ",\"projectId\":\""
                        + otherProject
                        + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void supplierPlantsLinkMaterialsWithoutDuplicatesOrCrossSupplierAccess() throws Exception {
    String supplier = supplier(),
        other = supplier(),
        plant = plant(supplier),
        material = material();
    String base = "/api/v1/suppliers/" + supplier + "/plants/" + plant;
    mvc.perform(put(base + "/materials/" + material).with(csrf())).andExpect(status().isOk());
    mvc.perform(put(base + "/materials/" + material).with(csrf())).andExpect(status().isOk());
    mvc.perform(get(base + "/materials"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].materialId").value(material));
    mvc.perform(get("/api/v1/suppliers/" + other + "/plants/" + plant))
        .andExpect(status().isNotFound());
    mvc.perform(
            delete("/api/v1/suppliers/" + other + "/plants/" + plant + "/materials/" + material)
                .with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(delete(base + "/materials/" + material).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get(base + "/materials")).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void inactiveSupplierPlantAndMaterialBlockNewPlantMaterialLinks() throws Exception {
    String supplier = supplier(), plant = plant(supplier), material = material();
    String base = "/api/v1/suppliers/" + supplier + "/plants/" + plant;
    setActive("/api/v1/suppliers/" + supplier, false);
    mvc.perform(put(base + "/materials/" + material).with(csrf()))
        .andExpect(status().isBadRequest());
    setActive("/api/v1/suppliers/" + supplier, true);
    setActive(base, false);
    mvc.perform(put(base + "/materials/" + material).with(csrf()))
        .andExpect(status().isBadRequest());
    setActive(base, true);
    setActive("/api/v1/materials/" + material, false);
    mvc.perform(put(base + "/materials/" + material).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void masterDataPermissionsDistinguishAdminOperationsAccountsAndExternalRoles() throws Exception {
    String branch = branch(), customer = customer();
    mvc.perform(
            post("/api/v1/branches")
                .with(user("ops").roles("OPERATIONS"))
                .with(csrf())
                .contentType("application/json")
                .content(branchJson(code())))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/branches").with(user("accounts").roles("ACCOUNTS")))
        .andExpect(status().isOk());
    mvc.perform(
            post(projectPath(customer))
                .with(user("ops").roles("OPERATIONS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"branchId\":\""
                        + branch
                        + "\",\"code\":\"OPS\",\"name\":\"Operations project\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post(projectPath(customer))
                .with(user("accounts").roles("ACCOUNTS"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"branchId\":\"" + branch + "\",\"code\":\"ACC\",\"name\":\"No access\"}"))
        .andExpect(status().isForbidden());
    for (String role : java.util.List.of("CUSTOMER", "SUPPLIER", "DRIVER"))
      for (String path :
          java.util.List.of(
              "/api/v1/branches", "/api/v1/materials", "/api/v1/suppliers", projectPath(customer)))
        mvc.perform(get(path).with(user("external").roles(role))).andExpect(status().isForbidden());
  }

  @Test
  void duplicateCodesAndInvalidDataDoNotLeaveAuditOrOrphanRecords() throws Exception {
    String branchCode = code();
    String branch = create("/api/v1/branches", branchJson(branchCode));
    Integer before =
        jdbc.queryForObject(
            "select count(*) from audit_events where action='BRANCH_CREATED'", Integer.class);
    mvc.perform(
            post("/api/v1/branches")
                .with(csrf())
                .contentType("application/json")
                .content(branchJson(branchCode.toLowerCase(java.util.Locale.ROOT))))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where action='BRANCH_CREATED'", Integer.class))
        .isEqualTo(before);
    mvc.perform(
            post("/api/v1/suppliers")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"code\":\"INVALID\",\"name\":\"Missing"
                        + " GST\",\"mobile\":\"9876543210\",\"address\":\"Address\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/branches?size=101")).andExpect(status().isBadRequest());
    String customer = customer();
    mvc.perform(
            post(projectPath(customer))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"branchId\":\"00000000-0000-0000-0000-000000000001\",\"code\":\"BAD\",\"name\":\"Unknown"
                        + " branch\"}"))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "select actor from audit_events where target_id=? and action='BRANCH_CREATED'",
                String.class,
                UUID.fromString(branch)))
        .isEqualTo("master-admin");
  }
}
