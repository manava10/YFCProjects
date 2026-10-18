package com.yfctransport.identity;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {
  Optional<PasswordReset> findByTokenHash(String hash);

  List<PasswordReset> findByAccountIdAndUsedFalse(UUID id);
}
