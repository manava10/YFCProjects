package com.yfctransport.documents;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class DocumentController {
  enum Target {
    TRIP,
    INCIDENT,
    FUEL,
    EXPENSE,
    STOCK_ADJUSTMENT,
    STOCK_TRANSFER,
    STOCK_OPENING
  }

  private final DocumentService service;

  DocumentController(DocumentService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<Map<String, Object>> upload(
      @RequestParam UUID documentId,
      @RequestParam Target targetType,
      @RequestParam UUID targetId,
      @RequestPart MultipartFile file) {
    var result = service.upload(documentId, targetType, targetId, file);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed")) ? 200 : 201)
        .cacheControl(CacheControl.noStore())
        .body(result);
  }

  @GetMapping
  ResponseEntity<List<Map<String, Object>>> list(
      @RequestParam Target targetType, @RequestParam UUID targetId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.list(targetType, targetId));
  }

  @GetMapping("/{id}")
  ResponseEntity<Map<String, Object>> metadata(@PathVariable UUID id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
  }

  @GetMapping("/{id}/content")
  ResponseEntity<byte[]> download(@PathVariable UUID id) {
    var result = service.download(id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(result.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .contentType(MediaType.parseMediaType(result.mediaType()))
        .contentLength(result.bytes().length)
        .body(result.bytes());
  }
}
