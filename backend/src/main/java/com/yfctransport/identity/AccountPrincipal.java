package com.yfctransport.identity;

import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

class AccountPrincipal extends User {
  final UUID accountId;
  final long authVersion;

  AccountPrincipal(Account account) {
    super(
        account.username,
        account.passwordHash,
        account.enabled,
        true,
        true,
        true,
        List.of(new SimpleGrantedAuthority("ROLE_" + account.role.name())));
    this.accountId = account.id;
    this.authVersion = account.authVersion;
  }
}
