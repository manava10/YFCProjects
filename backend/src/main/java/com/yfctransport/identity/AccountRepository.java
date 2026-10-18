package com.yfctransport.identity;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByUsername(String username);

  long countByRoleAndEnabledTrue(Role role);
}
