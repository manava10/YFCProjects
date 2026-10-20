package com.yfctransport.documents;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.fuel.OperatingEvidence;
import com.yfctransport.inventory.InventoryEvidence;
import com.yfctransport.logistics.LogisticsEvidence;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
class DocumentService {
  static final int MAX_SIZE = 2097152;
  private static final String METADATA =
      "id,target_type,target_id,filename,media_type,byte_size,sha256,uploaded_by,uploaded_at";
  private final JdbcTemplate db;
  private final LogisticsEvidence logistics;
  private final OperatingEvidence operating;
  private final InventoryEvidence inventory;
  private final AuditTrail audit;

  DocumentService(
      JdbcTemplate db,
      LogisticsEvidence logistics,
      OperatingEvidence operating,
      InventoryEvidence inventory,
      AuditTrail audit) {
    this.db = db;
    this.logistics = logistics;
    this.operating = operating;
    this.inventory = inventory;
    this.audit = audit;
  }

  private String actor() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }

  private void lock(DocumentController.Target type, UUID id) {
    switch (type) {
      case TRIP -> logistics.lockTrip(id);
      case INCIDENT -> logistics.lockIncident(id);
      case FUEL -> operating.lockFuel(id);
      case EXPENSE -> operating.lockExpense(id);
      case STOCK_ADJUSTMENT -> inventory.lockAdjustment(id);
      case STOCK_TRANSFER -> inventory.lockTransfer(id);
      case STOCK_OPENING -> inventory.lockOpening(id);
    }
  }

  private org.springframework.web.server.ResponseStatusException fail(
      HttpStatus status, String reason) {
    return new org.springframework.web.server.ResponseStatusException(status, reason);
  }

  private Map<String, Object> view(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach(
        (key, value) -> {
          StringBuilder name = new StringBuilder();
          boolean upper = false;
          for (char c : key.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '_') upper = true;
            else {
              name.append(upper ? Character.toUpperCase(c) : c);
              upper = false;
            }
          }
          result.put(
              name.toString(), value instanceof java.sql.Timestamp t ? t.toInstant() : value);
        });
    return result;
  }

  Map<String, Object> get(UUID id) {
    var rows = db.queryForList("select " + METADATA + " from private_documents where id=?", id);
    if (rows.isEmpty()) throw fail(HttpStatus.NOT_FOUND, "Document not found");
    return view(rows.getFirst());
  }

  List<Map<String, Object>> list(DocumentController.Target type, UUID id) {
    lock(type, id);
    return db
        .queryForList(
            "select "
                + METADATA
                + " from private_documents where target_type=? and target_id=? order by"
                + " uploaded_at,id",
            type.name(),
            id)
        .stream()
        .map(this::view)
        .toList();
  }

  Map<String, Object> upload(
      UUID id, DocumentController.Target type, UUID target, MultipartFile file) {
    if (file.isEmpty()) throw fail(HttpStatus.BAD_REQUEST, "File is empty");
    if (file.getSize() > MAX_SIZE)
      throw fail(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum document size is 2 MiB");
    String name = file.getOriginalFilename();
    if (name == null
        || name.isBlank()
        || name.length() > 160
        || name.contains("/")
        || name.contains("\\")
        || name.contains("..")
        || name.codePoints().anyMatch(c -> Character.isISOControl(c)))
      throw fail(
          HttpStatus.BAD_REQUEST,
          "Use a simple filename of at most 160 characters without paths or control characters");
    byte[] bytes;
    try (var stream = file.getInputStream()) {
      bytes = stream.readNBytes(MAX_SIZE + 1);
    } catch (IOException ex) {
      throw fail(HttpStatus.BAD_REQUEST, "Cannot read uploaded file");
    }
    if (bytes.length > MAX_SIZE)
      throw fail(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum document size is 2 MiB");
    String media = detect(bytes, name);
    if (!media.equals(file.getContentType()))
      throw fail(HttpStatus.BAD_REQUEST, "File type does not match its declared content type");
    String digest;
    try {
      digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
    lock(type, target);
    var old = db.queryForList("select " + METADATA + " from private_documents where id=?", id);
    if (!old.isEmpty()) {
      var record = view(old.getFirst());
      if (!type.name().equals(record.get("targetType"))
          || !target.equals(record.get("targetId"))
          || !digest.equals(record.get("sha256"))
          || !name.equals(record.get("filename"))
          || !media.equals(record.get("mediaType")))
        throw fail(
            HttpStatus.CONFLICT, "Document ID already used with different content or target");
      record.put("replayed", true);
      return record;
    }
    if (db.queryForObject(
            "select count(*) from private_documents where target_type=? and target_id=?",
            Integer.class,
            type.name(),
            target)
        >= 30) throw fail(HttpStatus.CONFLICT, "Maximum 30 documents per record reached");
    db.update(
        "insert into"
            + " private_documents(id,target_type,target_id,filename,media_type,byte_size,sha256,content,uploaded_by)"
            + " values(?,?,?,?,?,?,?,?,?)",
        id,
        type.name(),
        target,
        name,
        media,
        bytes.length,
        digest,
        bytes,
        actor());
    audit.record("DOCUMENT_UPLOADED", "DOCUMENT", id, "Target=" + type + "/" + target);
    var result = get(id);
    result.put("replayed", false);
    return result;
  }

  private String detect(byte[] bytes, String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".pdf")
        && bytes.length >= 12
        && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
        && new String(
                bytes,
                Math.max(0, bytes.length - 1024),
                Math.min(1024, bytes.length),
                StandardCharsets.US_ASCII)
            .stripTrailing()
            .endsWith("%%EOF")) return "application/pdf";
    if ((lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
      boolean png =
          bytes.length > 8
              && Arrays.equals(
                  Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
      boolean jpeg =
          bytes.length > 3
              && bytes[0] == (byte) 255
              && bytes[1] == (byte) 216
              && bytes[2] == (byte) 255;
      if ((png && lower.endsWith(".png"))
          || (jpeg && (lower.endsWith(".jpg") || lower.endsWith(".jpeg")))) {
        try (var input =
            javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
          var readers = javax.imageio.ImageIO.getImageReaders(input);
          if (readers.hasNext()) {
            var reader = readers.next();
            try {
              reader.setInput(input);
              long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
              if (pixels > 0 && pixels <= 20000000 && reader.read(0) != null)
                return png ? "image/png" : "image/jpeg";
            } finally {
              reader.dispose();
            }
          }
        } catch (IOException | RuntimeException ex) {
          throw fail(HttpStatus.BAD_REQUEST, "Invalid image file");
        }
      }
    }
    throw fail(
        HttpStatus.BAD_REQUEST,
        "Only PDF, JPEG and PNG files with matching filename and content are accepted");
  }

  record Download(String filename, String mediaType, byte[] bytes) {}

  Download download(UUID id) {
    var metadata = get(id);
    byte[] bytes =
        db.queryForObject("select content from private_documents where id=?", byte[].class, id);
    audit.record("DOCUMENT_DOWNLOADED", "DOCUMENT", id, "Authenticated staff download");
    return new Download(
        (String) metadata.get("filename"), (String) metadata.get("mediaType"), bytes);
  }
}
