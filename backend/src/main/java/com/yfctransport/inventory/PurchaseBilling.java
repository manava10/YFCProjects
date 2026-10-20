package com.yfctransport.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable received-weight evidence and agreed purchase rate, before tax. */
public interface PurchaseBilling {
  record Receipt(
      UUID receiptId,
      UUID purchaseId,
      UUID supplierId,
      UUID branchId,
      String supplierName,
      String material,
      String terms,
      LocalDate receivedOn,
      BigDecimal loadedQuantity,
      BigDecimal receivedQuantity,
      BigDecimal acceptedQuantity,
      BigDecimal rate,
      String reference) {}

  Receipt receipt(UUID id);
}
