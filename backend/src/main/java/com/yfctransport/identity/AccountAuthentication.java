package com.yfctransport.identity;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountAuthentication implements UserDetailsService {
  private final AccountRepository accounts;

  AccountAuthentication(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) {
    return accounts
        .findByUsername(username.toLowerCase(java.util.Locale.ROOT))
        .map(AccountPrincipal::new)
        .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }
}
