package com.yfctransport.fleet;

import java.time.LocalDate;
import java.util.UUID;

public interface DriverOpening {
  UUID open(
      UUID branchId,
      String name,
      String mobile,
      String licenseNumber,
      LocalDate licenseExpiry,
      String emergencyContact,
      String emergencyMobile);
}
