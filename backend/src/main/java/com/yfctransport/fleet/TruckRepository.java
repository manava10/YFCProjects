package com.yfctransport.fleet;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TruckRepository extends JpaRepository<Truck, UUID> {}
