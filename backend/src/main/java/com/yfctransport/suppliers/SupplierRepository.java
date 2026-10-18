package com.yfctransport.suppliers;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface SupplierRepository extends JpaRepository<Supplier, UUID> {}
