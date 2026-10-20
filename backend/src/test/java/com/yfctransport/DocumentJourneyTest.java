package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "document-admin", roles = "SUPER_ADMIN")
class DocumentJourneyTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  OperatingJourneyTest h;
  String expense;

  @BeforeEach
  void setup() throws Exception {
    h = new OperatingJourneyTest();
    h.mvc = mvc;
    h.json = json;
    h.db = db;
    h.setup();
    var fixture = h.fixture();
    expense = h.id(h.create("/api/v1/expenses", h.expense(fixture, null, "REPAIR")));
  }

  byte[] pdf() {
    return "%PDF-1.4\n%Document test fixture\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
  }

  MockMultipartFile file(String name, String type, byte[] bytes) {
    return new MockMultipartFile("file", name, type, bytes);
  }

  int upload(String id, String target, String targetId, MockMultipartFile file, String role)
      throws Exception {
    return mvc.perform(
            multipart("/api/v1/documents")
                .file(file)
                .param("documentId", id)
                .param("targetType", target)
                .param("targetId", targetId)
                .with(user("document-user").roles(role))
                .with(csrf()))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  String uid() {
    return UUID.randomUUID().toString();
  }

  @Test
  void uploadDownloadListAndAudit() throws Exception {
    String id = uid();
    assertThat(
            upload(
                id, "EXPENSE", expense, file("receipt.pdf", "application/pdf", pdf()), "ACCOUNTS"))
        .isEqualTo(201);
    mvc.perform(get("/api/v1/documents/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadedBy").value("document-user"))
        .andExpect(jsonPath("$.byteSize").value(pdf().length))
        .andExpect(jsonPath("$.content").doesNotExist());
    mvc.perform(get("/api/v1/documents").param("targetType", "EXPENSE").param("targetId", expense))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get("/api/v1/documents/" + id + "/content"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(
            header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
        .andExpect(content().bytes(pdf()));
    assertThat(
            db.queryForObject(
                "select count(*) from audit_events where target_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(2);
  }

  @Test
  void identicalRetryAndConflictingId() throws Exception {
    String id = uid();
    var f = file("receipt.pdf", "application/pdf", pdf());
    assertThat(upload(id, "EXPENSE", expense, f, "OPERATIONS")).isEqualTo(201);
    assertThat(upload(id, "EXPENSE", expense, f, "OPERATIONS")).isEqualTo(200);
    assertThat(
            upload(
                id,
                "EXPENSE",
                expense,
                file("renamed.pdf", "application/pdf", pdf()),
                "OPERATIONS"))
        .isEqualTo(409);
    assertThat(
            db.queryForObject(
                "select count(*) from audit_events where target_id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void filenamesTypesEmptyAndOversizeAreRejected() throws Exception {
    for (String name :
        List.of("../receipt.pdf", "folder/receipt.pdf", "C:\\receipt.pdf", "receipt\r\n.pdf"))
      assertThat(
              upload(uid(), "EXPENSE", expense, file(name, "application/pdf", pdf()), "OPERATIONS"))
          .isEqualTo(400);
    assertThat(
            upload(
                uid(), "EXPENSE", expense, file("receipt.pdf", "text/html", pdf()), "OPERATIONS"))
        .isEqualTo(400);
    assertThat(
            upload(
                uid(),
                "EXPENSE",
                expense,
                file("receipt.pdf", "application/pdf", "<html>bad</html>".getBytes()),
                "OPERATIONS"))
        .isEqualTo(400);
    assertThat(
            upload(
                uid(),
                "EXPENSE",
                expense,
                file("receipt.pdf", "application/pdf", new byte[0]),
                "OPERATIONS"))
        .isEqualTo(400);
    assertThat(
            upload(
                uid(),
                "EXPENSE",
                expense,
                file("receipt.pdf", "application/pdf", new byte[2097153]),
                "OPERATIONS"))
        .isEqualTo(413);
  }

  @Test
  void imagesRequireValidContentAndMatchingExtension() throws Exception {
    var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var stream = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", stream);
    assertThat(
            upload(
                uid(),
                "EXPENSE",
                expense,
                file("photo.png", "image/png", stream.toByteArray()),
                "OPERATIONS"))
        .isEqualTo(201);
    assertThat(
            upload(
                uid(),
                "EXPENSE",
                expense,
                file("photo.jpg", "image/jpeg", stream.toByteArray()),
                "OPERATIONS"))
        .isEqualTo(400);
    assertThat(
            upload(
                uid(),
                "EXPENSE",
                expense,
                file(
                    "photo.png",
                    "image/png",
                    new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0}),
                "OPERATIONS"))
        .isEqualTo(400);
  }

  @Test
  void externalRolesAnonymousAndMissingCsrfDenied() throws Exception {
    String id = uid();
    assertThat(
            upload(
                id,
                "EXPENSE",
                expense,
                file("receipt.pdf", "application/pdf", pdf()),
                "OPERATIONS"))
        .isEqualTo(201);
    for (String role : List.of("CUSTOMER", "DRIVER", "SUPPLIER")) {
      assertThat(
              upload(
                  uid(), "EXPENSE", expense, file("receipt.pdf", "application/pdf", pdf()), role))
          .isEqualTo(403);
      mvc.perform(get("/api/v1/documents/" + id + "/content").with(user("external").roles(role)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/v1/documents/" + id).with(anonymous()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            multipart("/api/v1/documents")
                .file(file("receipt.pdf", "application/pdf", pdf()))
                .param("documentId", uid())
                .param("targetType", "EXPENSE")
                .param("targetId", expense))
        .andExpect(status().isForbidden());
  }

  @Test
  void missingTargetsAndDocumentsReturn404() throws Exception {
    for (String target : List.of("TRIP", "INCIDENT", "FUEL", "EXPENSE"))
      assertThat(
              upload(
                  uid(),
                  target,
                  uid(),
                  file("receipt.pdf", "application/pdf", pdf()),
                  "OPERATIONS"))
          .isEqualTo(404);
    mvc.perform(get("/api/v1/documents/" + uid() + "/content")).andExpect(status().isNotFound());
  }

  @Test
  void allSupportedTargetsCanAttach() throws Exception {
    var fixture = h.fixture();
    String fuel = h.id(h.create("/api/v1/fuel/entries", h.fuel(fixture, h.now(), "1000")));
    assertThat(
            upload(
                uid(), "FUEL", fuel, file("receipt.pdf", "application/pdf", pdf()), "OPERATIONS"))
        .isEqualTo(201);
    var t = new IncidentJourneyTest();
    t.mvc = mvc;
    t.json = json;
    t.db = db;
    t.setup();
    var report = t.report();
    t.h.action(t.base(), report, 201);
    for (var target :
        Map.of("TRIP", t.trip, "INCIDENT", report.get("incidentId").toString()).entrySet())
      assertThat(
              upload(
                  uid(),
                  target.getKey(),
                  target.getValue(),
                  file("receipt.pdf", "application/pdf", pdf()),
                  "OPERATIONS"))
          .isEqualTo(201);
  }

  @Test
  void concurrentIdenticalUploadsHaveOneStoredCopy() throws Exception {
    String id = uid();
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Integer> task =
          () -> {
            gate.await();
            return upload(
                id,
                "EXPENSE",
                expense,
                file("receipt.pdf", "application/pdf", pdf()),
                "OPERATIONS");
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 200);
    }
    assertThat(
            db.queryForObject(
                "select count(*) from private_documents where id=?",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void targetDocumentLimitAndExistingRetry() throws Exception {
    String first = uid();
    assertThat(
            upload(
                first,
                "EXPENSE",
                expense,
                file("receipt.pdf", "application/pdf", pdf()),
                "OPERATIONS"))
        .isEqualTo(201);
    for (int i = 1; i < 30; i++)
      assertThat(
              upload(
                  uid(),
                  "EXPENSE",
                  expense,
                  file("receipt.pdf", "application/pdf", pdf()),
                  "OPERATIONS"))
          .isEqualTo(201);
    assertThat(
            upload(
                uid(),
                "EXPENSE",
                expense,
                file("receipt.pdf", "application/pdf", pdf()),
                "OPERATIONS"))
        .isEqualTo(409);
    assertThat(
            upload(
                first,
                "EXPENSE",
                expense,
                file("receipt.pdf", "application/pdf", pdf()),
                "OPERATIONS"))
        .isEqualTo(200);
  }
}
