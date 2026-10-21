package com.yfctransport.identity;

import java.util.UUID;

public interface ExternalAccounts {
  void lock();

  boolean usernameTaken(String username);

  void validatePassword(String password);

  String encode(String password);

  boolean passwordMatches(String raw, String hash);

  UUID openHashed(String username, String displayName, String passwordHash, String role);

  UUID idOf(String username);
}
