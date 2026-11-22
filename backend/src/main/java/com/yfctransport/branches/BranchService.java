package com.yfctransport.branches;

import com.yfctransport.audit.AuditTrail;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class BranchService implements BranchRegistry {
  private final BranchRepository branches;
  private final AuditTrail audit;

  BranchService(BranchRepository branches, AuditTrail audit) {
    this.branches = branches;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  Branch find(UUID id) {
    return branches
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
  }

  @Transactional(readOnly = true)
  public void requireActive(UUID id) {
    if (id == null || !branches.existsById(id))
      throw new IllegalArgumentException("Branch does not exist");
    if (!find(id).active) throw new IllegalArgumentException("Branch is inactive");
  }

  @Transactional(readOnly = true)
  public BranchTaxProfile profile(UUID id) {
    Branch a = find(id);
    String stateCode =
        a.stateCode != null && !a.stateCode.isBlank()
            ? a.stateCode
            : (a.gstin != null && a.gstin.length() >= 2 ? a.gstin.substring(0, 2) : null);
    return new BranchTaxProfile(
        a.id,
        a.code,
        a.name,
        a.address,
        a.city,
        a.state,
        a.pincode,
        a.gstin,
        stateCode,
        a.bankName,
        a.bankAccount,
        a.bankIfsc);
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  Branch save(UUID id, BranchController.WriteBranch r) {
    Branch a = id == null ? new Branch() : find(id);
    a.code = r.code().strip().toUpperCase(Locale.ROOT);
    a.name = r.name().strip();
    a.address = r.address().strip();
    a.city = r.city().strip();
    a.state = r.state().strip();
    a.pincode = r.pincode();
    String gstin = clean(r.gstin());
    a.gstin = gstin == null ? null : gstin.toUpperCase(Locale.ROOT);
    String stateCode = clean(r.stateCode());
    if (stateCode == null && a.gstin != null && a.gstin.length() >= 2) {
      stateCode = a.gstin.substring(0, 2);
    }
    if (a.gstin != null && stateCode != null && !a.gstin.startsWith(stateCode)) {
      throw new IllegalArgumentException("Branch state code must match the first 2 digits of GSTIN");
    }
    a.stateCode = stateCode;
    a.bankName = clean(r.bankName());
    a.bankAccount = clean(r.bankAccount());
    String ifsc = clean(r.bankIfsc());
    a.bankIfsc = ifsc == null ? null : ifsc.toUpperCase(Locale.ROOT);
    branches.save(a);
    audit.record(
        id == null ? "BRANCH_CREATED" : "BRANCH_UPDATED", "BRANCH", a.id, "Branch master saved");
    return a;
  }

  Branch status(UUID id, boolean active) {
    Branch a = find(id);
    a.active = active;
    audit.record("BRANCH_STATUS_CHANGED", "BRANCH", id, "Active=" + active);
    return a;
  }
}
