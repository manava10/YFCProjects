package com.yfctransport.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/gst-notes")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNTS')")
class GstNoteController {
  private final GstNoteService service;

  GstNoteController(GstNoteService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> create(@Valid @RequestBody GstNoteService.CreateNote r) {
    return service.create(r);
  }

  @GetMapping
  List<Map<String, Object>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) GstNoteService.NoteType noteType,
      @RequestParam(required = false) @Size(max = 20) String status,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) UUID originalPreparationId,
      @RequestParam(required = false) @Size(max = 120) String q) {
    return service.list(page, size, noteType, status, branchId, originalPreparationId, q);
  }

  @GetMapping("/{id}")
  Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/approve")
  Map<String, Object> approve(
      @PathVariable UUID id, @Valid @RequestBody GstNoteService.Decision r) {
    return service.decide(id, r, true);
  }

  @PostMapping("/{id}/cancel")
  Map<String, Object> cancel(
      @PathVariable UUID id, @Valid @RequestBody GstNoteService.Decision r) {
    return service.decide(id, r, false);
  }
}
