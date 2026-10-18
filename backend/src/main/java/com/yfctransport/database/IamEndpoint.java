package com.yfctransport.database;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

record IamEndpoint(String host, int port) {
  static IamEndpoint from(String jdbcUrl) {
    if (!jdbcUrl.startsWith("jdbc:postgresql://"))
      throw new IllegalArgumentException("IAM authentication requires a PostgreSQL JDBC URL");
    URI uri = URI.create(jdbcUrl.substring(5));
    if (uri.getHost() == null
        || !uri.getHost().endsWith(".rds.amazonaws.com")
        || uri.getUserInfo() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException(
          "Use the actual AWS RDS endpoint without embedded credentials");
    Map<String, String> params = new HashMap<>();
    for (String entry : Optional.ofNullable(uri.getRawQuery()).orElse("").split("&")) {
      if (entry.isEmpty()) continue;
      String[] pair = entry.split("=", 2);
      String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
      if (params.putIfAbsent(
              key, pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
          != null)
        throw new IllegalArgumentException("Duplicate JDBC URL parameters are not allowed");
    }
    if (!"verify-full".equals(params.get("sslmode"))
        || !params.containsKey("sslrootcert")
        || params.get("sslrootcert").isBlank())
      throw new IllegalArgumentException(
          "IAM database access requires sslmode=verify-full and sslrootcert");
    if (params.containsKey("password")
        || params.containsKey("user")
        || params.containsKey("sslfactory")
        || params.containsKey("sslhostnameverifier"))
      throw new IllegalArgumentException(
          "Do not embed credentials or override the TLS factory in the JDBC URL");
    if (uri.getPath() == null || uri.getPath().length() < 2)
      throw new IllegalArgumentException("Specify an existing PostgreSQL database in DB_URL");
    int port = uri.getPort() == -1 ? 5432 : uri.getPort();
    if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid database port");
    return new IamEndpoint(uri.getHost(), port);
  }
}
