package com.yfctransport.documents;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LocalDocumentBlobStore implements DocumentBlobStore {
  private final Path root;

  LocalDocumentBlobStore(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  @Override
  public String backendName() {
    return "LOCAL";
  }

  @Override
  public String put(UUID id, String targetType, String filename, String mediaType, byte[] bytes) {
    String key = targetType.toLowerCase(Locale.ROOT) + "/" + id;
    Path path = resolveSafe(key);
    try {
      Files.createDirectories(path.getParent());
      Path tmp = Files.createTempFile(path.getParent(), id.toString(), ".tmp");
      try {
        Files.write(tmp, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(tmp);
      }
      return key;
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to persist document in local storage", ex);
    }
  }

  @Override
  public byte[] get(String storageKey) {
    Path path = resolveSafe(storageKey);
    try {
      if (!Files.exists(path))
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored document content not found");
      return Files.readAllBytes(path);
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read document from local storage", ex);
    }
  }

  @Override
  public void deleteQuietly(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) return;
    try {
      Files.deleteIfExists(resolveSafe(storageKey));
    } catch (Exception ignored) {
    }
  }

  private Path resolveSafe(String key) {
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage key");
    return path;
  }
}
