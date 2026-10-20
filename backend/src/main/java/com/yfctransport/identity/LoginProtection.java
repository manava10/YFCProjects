package com.yfctransport.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class LoginProtection {
  static final int LIMIT = 5;
  static final int WINDOW_MINUTES = 15;
  private final JdbcTemplate db;
  private final Clock clock;

  LoginProtection(JdbcTemplate db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  private String name(String username) {
    return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
  }

  boolean locked(String username) {
    String key = name(username);
    if (key.isBlank()) return false;
    Instant since = clock.instant().minusSeconds(WINDOW_MINUTES * 60L);
    Integer count =
        db.queryForObject(
            "select count(*) from login_failures where username=? and failed_at>=?",
            Integer.class,
            key,
            java.sql.Timestamp.from(since));
    return count != null && count >= LIMIT;
  }

  void failed(String username) {
    String key = name(username);
    if (key.isBlank() || key.length() > 80) return;
    db.update("insert into login_failures(username,failed_at) values(?,?)", key, java.sql.Timestamp.from(clock.instant()));
  }

  void succeeded(String username) {
    String key = name(username);
    if (key.isBlank()) return;
    db.update("delete from login_failures where username=?", key);
  }
}
