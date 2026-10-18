package com.yfctransport.orders;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface DispatchRepository extends JpaRepository<Dispatch, UUID> {
  List<Dispatch> findByOrderId(UUID orderId);
}
