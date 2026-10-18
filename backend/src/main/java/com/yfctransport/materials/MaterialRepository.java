package com.yfctransport.materials;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface MaterialRepository extends JpaRepository<Material, UUID> {}
