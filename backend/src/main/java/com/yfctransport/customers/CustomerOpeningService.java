package com.yfctransport.customers;

import com.yfctransport.audit.AuditTrail;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerOpeningService implements CustomerOpening {
  private final CustomerRepository customers;
  private final AuditTrail audit;

  CustomerOpeningService(CustomerRepository customers, AuditTrail audit) {
    this.customers = customers;
    this.audit = audit;
  }

  @Override
  @Transactional
  public UUID open(String name, String mobile, String gstin, String address) {
    Customer c = customers.save(new Customer(name.strip(), mobile.strip(), gstin, address));
    audit.record("CUSTOMER_CREATED", "CUSTOMER", c.id, "Opened from an approved account application");
    return c.id;
  }
}
