package com.yfctransport.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test | local")
class BootstrapAdmin implements ApplicationRunner {
  private final AccountService accounts;
  private final String username;
  private final String password;

  BootstrapAdmin(
      AccountService accounts,
      @Value("${YFC_ADMIN_USERNAME:admin}") String username,
      @Value("${YFC_ADMIN_PASSWORD:}") String password) {
    this.accounts = accounts;
    this.username = username;
    this.password = password;
  }

  public void run(ApplicationArguments args) {
    accounts.bootstrap(username, password);
  }
}
