package com.yfctransport.customers;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CustomerRepository extends JpaRepository<Customer, UUID> {
  @Query(
      "select c from Customer c where lower(c.name) like lower(concat('%', :q, '%'))"
          + " or lower(c.mobile) like lower(concat('%', :q, '%'))"
          + " or lower(coalesce(c.gstin, '')) like lower(concat('%', :q, '%'))")
  Page<Customer> search(@Param("q") String q, Pageable pageable);
}
