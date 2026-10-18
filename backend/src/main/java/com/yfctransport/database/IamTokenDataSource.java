package com.yfctransport.database;

import java.sql.*;
import java.util.Properties;
import java.util.function.Supplier;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Generates a new token for each physical connection; the pool never retains a fixed password. */
class IamTokenDataSource extends AbstractDataSource {
  @FunctionalInterface
  interface ConnectionOpener {
    Connection open(String url, Properties properties) throws SQLException;
  }

  private final String url;
  private final String username;
  private final Supplier<String> tokens;
  private final ConnectionOpener opener;

  IamTokenDataSource(String url, String username, Supplier<String> tokens) {
    this(url, username, tokens, DriverManager::getConnection);
  }

  IamTokenDataSource(
      String url, String username, Supplier<String> tokens, ConnectionOpener opener) {
    this.url = url;
    this.username = username;
    this.tokens = tokens;
    this.opener = opener;
  }

  @Override
  public Connection getConnection() throws SQLException {
    String token;
    try {
      token = tokens.get();
      if (token == null || token.isBlank()) throw new IllegalStateException();
    } catch (RuntimeException e) {
      throw new SQLException(
          "Cannot obtain IAM database credentials. Sign in to AWS and check database-connect"
              + " permissions.",
          "28000");
    }
    Properties properties = new Properties();
    properties.setProperty("user", username);
    properties.setProperty("password", token);
    properties.setProperty("connectTimeout", "10");
    properties.setProperty("socketTimeout", "60");
    try {
      return opener.open(url, properties);
    } finally {
      properties.remove("password");
    }
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    throw new SQLFeatureNotSupportedException(
        "IAM credentials are supplied by the configured AWS identity");
  }
}
