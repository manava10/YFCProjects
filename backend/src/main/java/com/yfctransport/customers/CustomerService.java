package com.yfctransport.customers;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CustomerService implements CustomerRegistry {
  private final CustomerRepository customers;

  CustomerService(CustomerRepository customers) {
    this.customers = customers;
  }

  public boolean exists(UUID id) {
    return customers.existsById(id);
  }

  @Override
  public String name(UUID id) {
    return customers
        .findById(id)
        .orElseThrow(
            () ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Customer not found"))
        .name;
  }
}
