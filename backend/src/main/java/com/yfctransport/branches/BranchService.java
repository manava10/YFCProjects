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

  Branch save(UUID id, BranchController.WriteBranch r) {
    Branch a = id == null ? new Branch() : find(id);
    a.code = r.code().strip().toUpperCase(Locale.ROOT);
    a.name = r.name().strip();
    a.address = r.address().strip();
    a.city = r.city().strip();
    a.state = r.state().strip();
    a.pincode = r.pincode();
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
