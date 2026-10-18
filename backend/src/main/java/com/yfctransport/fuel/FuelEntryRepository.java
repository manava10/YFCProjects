package com.yfctransport.fuel;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FuelEntryRepository extends JpaRepository<FuelEntry, UUID> {}
