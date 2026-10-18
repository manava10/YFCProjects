package com.yfctransport.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class SalesDtos {
  enum BusinessType {
    MATERIAL,
    TRANSPORT,
    MATERIAL_AND_TRANSPORT
  }

  record RfqLine(
      @NotNull UUID materialId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity) {}

  record CreateRfq(
      @NotNull UUID customerId,
      @NotNull UUID deliverySiteId,
      @NotNull BusinessType businessType,
      @NotNull LocalDate requiredDate,
      @Size(max = 2000) String remarks,
      @NotEmpty @Size(max = 100) List<@NotNull @Valid RfqLine> items) {}

  record RfqLineView(
      UUID id, int position, UUID materialId, String material, String unit, BigDecimal quantity) {}

  record RfqView(
      UUID id,
      UUID customerId,
      UUID branchId,
      UUID projectId,
      UUID deliverySiteId,
      String deliveryAddress,
      String businessType,
      LocalDate requiredDate,
      String remarks,
      String status,
      Instant createdAt,
      List<RfqLineView> items) {}

  record QuotePrice(
      @NotNull UUID rfqItemId,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal materialRate,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal transportRate) {}

  record CreateQuotation(
      @NotNull LocalDate validUntil,
      @NotBlank @Size(max = 1000) String paymentTerms,
      @NotBlank @Size(max = 1000) String deliveryTerms,
      @NotEmpty @Size(max = 100) List<@NotNull @Valid QuotePrice> items) {}

  record AcceptQuotation(
      @NotBlank @Size(max = 160) String customerContact,
      @NotBlank @Size(max = 300) String approvalReference,
      @Size(max = 1000) String note) {}

  record RejectQuotation(
      @NotBlank @Size(max = 160) String customerContact,
      @NotBlank @Size(max = 300) String approvalReference,
      @NotBlank @Size(max = 1000) String reason) {}

  record QuoteLineView(
      UUID rfqItemId,
      int position,
      UUID materialId,
      String material,
      String unit,
      BigDecimal quantity,
      BigDecimal materialRate,
      BigDecimal transportRate,
      BigDecimal materialAmount,
      BigDecimal transportAmount,
      BigDecimal subtotal) {}

  record QuoteView(
      UUID id,
      UUID rfqId,
      int revision,
      String status,
      boolean expired,
      LocalDate validUntil,
      String paymentTerms,
      String deliveryTerms,
      String currency,
      String billingBasis,
      String taxTreatment,
      BigDecimal materialTotal,
      BigDecimal transportTotal,
      BigDecimal subtotal,
      Instant sentAt,
      Instant decidedAt,
      String recordedBy,
      String customerContact,
      String approvalReference,
      String decisionNote,
      UUID convertedOrderId,
      List<QuoteLineView> items) {}

  record Conversion(UUID orderId, UUID quotationId, UUID rfqId, boolean created) {}
}
