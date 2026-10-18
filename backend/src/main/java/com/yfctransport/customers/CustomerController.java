package com.yfctransport.customers;

import com.yfctransport.audit.AuditTrail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class CustomerController {
  private final CustomerRepository customers;
  private final AuditTrail audit;

  CustomerController(CustomerRepository customers, AuditTrail audit) {
    this.customers = customers;
    this.audit = audit;
  }

  record CreateCustomer(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}") String mobile,
      @Pattern(regexp = "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]") String gstin) {}

  record CustomerView(UUID id, String name, String mobile, String gstin) {
    static CustomerView of(Customer c) {
      return new CustomerView(c.id, c.name, c.mobile, c.gstin);
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  @org.springframework.transaction.annotation.Transactional
  CustomerView create(@Valid @RequestBody CreateCustomer r) {
    Customer c = customers.save(new Customer(r.name().strip(), r.mobile().strip(), r.gstin()));
    audit.record("CUSTOMER_CREATED", "CUSTOMER", c.id, "Customer registered");
    return CustomerView.of(c);
  }

  @GetMapping
  Page<CustomerView> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return customers
        .findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))
        .map(CustomerView::of);
  }
}
