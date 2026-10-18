package com.yfctransport.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class SalesController {
  private final SalesService sales;
  private final RfqRepository rfqs;

  SalesController(SalesService sales, RfqRepository rfqs) {
    this.sales = sales;
    this.rfqs = rfqs;
  }

  @PostMapping("/rfqs")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SalesDtos.RfqView create(@Valid @RequestBody SalesDtos.CreateRfq r) {
    return sales.create(r);
  }

  @GetMapping("/rfqs/{id}")
  SalesDtos.RfqView get(@PathVariable UUID id) {
    return sales.rfqView(sales.rfq(id));
  }

  @GetMapping("/rfqs")
  Page<SalesDtos.RfqView> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return rfqs.findAll(
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
        .map(sales::rfqView);
  }

  @GetMapping("/rfqs/{id}/quotations")
  java.util.List<SalesDtos.QuoteView> history(@PathVariable UUID id) {
    return sales.quotationHistory(id);
  }

  @PostMapping("/rfqs/{id}/quotations")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SalesDtos.QuoteView quote(
      @PathVariable UUID id, @Valid @RequestBody SalesDtos.CreateQuotation r) {
    return sales.createQuotation(id, r);
  }

  @GetMapping("/quotations/{id}")
  SalesDtos.QuoteView quotation(@PathVariable UUID id) {
    return sales.quotation(id);
  }

  @PostMapping("/quotations/{id}/send")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SalesDtos.QuoteView send(@PathVariable UUID id) {
    return sales.send(id);
  }

  @PostMapping("/quotations/{id}/accept")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SalesDtos.QuoteView accept(
      @PathVariable UUID id, @Valid @RequestBody SalesDtos.AcceptQuotation r) {
    return sales.accept(id, r);
  }

  @PostMapping("/quotations/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  SalesDtos.QuoteView reject(
      @PathVariable UUID id, @Valid @RequestBody SalesDtos.RejectQuotation r) {
    return sales.reject(id, r);
  }

  @PostMapping("/quotations/{id}/convert")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  ResponseEntity<SalesDtos.Conversion> convert(@PathVariable UUID id) {
    var result = sales.convert(id);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result);
  }
}
