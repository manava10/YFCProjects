package com.yfctransport.database;

import java.net.URI;
import java.sql.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;

/** Explicit maintenance command; never called during normal application startup. */
public class AwsDatabaseTool {
  public static void main(String[] args) throws Exception {
    String command = args.length == 0 ? "check" : args[0];
    if (command.equals("help")) {
      System.out.println(
          "Commands: check (read-only) or create-databases (create yfc/yfc_test if absent)");
      return;
    }
    if (!command.equals("check") && !command.equals("create-databases"))
      throw new IllegalArgumentException("Use check or create-databases");
    String configured = required("DB_URL"),
        username = required("DB_USER"),
        region = required("AWS_REGION");
    IamEndpoint endpoint = IamEndpoint.from(configured);
    URI uri = URI.create(configured.substring(5));
    String bootstrap =
        "jdbc:postgresql://" + uri.getRawAuthority() + "/postgres?" + uri.getRawQuery();
    try (var credentials = DefaultCredentialsProvider.builder().build()) {
      var utilities =
          RdsUtilities.builder().credentialsProvider(credentials).region(Region.of(region)).build();
      var source =
          new IamTokenDataSource(
              bootstrap,
              username,
              () ->
                  utilities.generateAuthenticationToken(
                      r -> r.hostname(endpoint.host()).port(endpoint.port()).username(username)));
      try (Connection connection = source.getConnection()) {
        if (command.equals("check")) {
          connection.setReadOnly(true);
          try (var query =
                  connection.prepareStatement(
                      "select datname from pg_database where datname in"
                          + " ('postgres','yfc','yfc_test') order by datname");
              var rows = query.executeQuery()) {
            System.out.println("IAM database login succeeded. Existing databases:");
            while (rows.next()) System.out.println(rows.getString(1));
          }
        } else {
          for (String database : new String[] {"yfc", "yfc_test"}) {
            boolean exists;
            try (var query =
                connection.prepareStatement("select 1 from pg_database where datname=?")) {
              query.setString(1, database);
              try (var rows = query.executeQuery()) {
                exists = rows.next();
              }
            }
            if (!exists) {
              try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE " + database);
              } catch (SQLException error) {
                if (!"42P04".equals(error.getSQLState())) throw error;
              }
            }
            System.out.println(database + ": ready");
          }
        }
      }
    }
  }

  private static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Set " + key + " in the AWS environment file");
    return value;
  }
}
