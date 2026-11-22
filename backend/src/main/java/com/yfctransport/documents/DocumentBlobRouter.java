package com.yfctransport.documents;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Routes document uploads to the configured active storage backend (DATABASE, LOCAL, or S3) while
 * supporting seamless hybrid downloads across previously stored backends during migrations.
 */
@Component
class DocumentBlobRouter {
  private final String activeBackend;
  private final LocalDocumentBlobStore local;
  private final S3DocumentBlobStore s3;

  DocumentBlobRouter(
      @Value("${yfc.documents.storage:DATABASE}") String storage,
      @Value("${yfc.documents.local-dir:./data/documents}") String localDir,
      @Value("${yfc.documents.s3.bucket:}") String s3Bucket,
      @Value("${yfc.documents.s3.region:ap-south-2}") String s3Region,
      @Value("${yfc.documents.s3.prefix:yfc-documents}") String s3Prefix,
      @Value("${yfc.documents.s3.endpoint:}") String s3Endpoint) {
    String normalized = storage == null ? "DATABASE" : storage.strip().toUpperCase(Locale.ROOT);
    if (!normalized.equals("DATABASE") && !normalized.equals("LOCAL") && !normalized.equals("S3")) {
      throw new IllegalArgumentException(
          "Unsupported yfc.documents.storage value: " + storage + " (expected DATABASE, LOCAL, or S3)");
    }
    this.activeBackend = normalized;
    this.local = new LocalDocumentBlobStore(Path.of(localDir));
    this.s3 = new S3DocumentBlobStore(s3Bucket, s3Region, s3Prefix, s3Endpoint);
  }

  String activeBackend() {
    return activeBackend;
  }

  String storeExternal(UUID id, String targetType, String filename, String mediaType, byte[] bytes) {
    return switch (activeBackend) {
      case "LOCAL" -> local.put(id, targetType, filename, mediaType, bytes);
      case "S3" -> s3.put(id, targetType, filename, mediaType, bytes);
      default -> null;
    };
  }

  byte[] readExternal(String backend, String storageKey) {
    return switch (backend) {
      case "LOCAL" -> local.get(storageKey);
      case "S3" -> s3.get(storageKey);
      default ->
          throw new ResponseStatusException(
              HttpStatus.INTERNAL_SERVER_ERROR, "Unknown document storage backend: " + backend);
    };
  }

  void deleteExternalQuietly(String backend, String storageKey) {
    if ("LOCAL".equals(backend)) local.deleteQuietly(storageKey);
    else if ("S3".equals(backend)) s3.deleteQuietly(storageKey);
  }
}
