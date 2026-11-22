package com.yfctransport.branches;

import java.util.UUID;

public interface BranchRegistry {
  record BranchTaxProfile(
      UUID id,
      String code,
      String name,
      String address,
      String city,
      String state,
      String pincode,
      String gstin,
      String stateCode,
      String bankName,
      String bankAccount,
      String bankIfsc) {}

  void requireActive(UUID id);

  BranchTaxProfile profile(UUID id);
}
