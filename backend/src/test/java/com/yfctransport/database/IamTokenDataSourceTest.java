package com.yfctransport.database;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IamTokenDataSourceTest {
  static final String HOST = "example.cluster-abc.ap-south-2.rds.amazonaws.com";
  static final String URL =
      "jdbc:postgresql://" + HOST + ":5432/yfc?sslmode=verify-full&sslrootcert=/tmp/ca.pem";

  @Test
  void tokenIsGeneratedForEveryPhysicalConnectionAndNotRetainedInProperties() throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    List<String> used = new ArrayList<>();
    List<Properties> retained = new ArrayList<>();
    var source =
        new IamTokenDataSource(
            URL,
            "postgres",
            () -> "temporary-" + sequence.incrementAndGet(),
            (url, p) -> {
              assertThat(url).isEqualTo(URL);
              assertThat(p.getProperty("user")).isEqualTo("postgres");
              used.add(p.getProperty("password"));
              retained.add(p);
              return mock(Connection.class);
            });
    try (var first = source.getConnection();
        var second = source.getConnection()) {
      assertThat(used).containsExactly("temporary-1", "temporary-2");
      assertThat(retained).allSatisfy(p -> assertThat(p).doesNotContainKey("password"));
    }
  }

  @Test
  void credentialErrorsDoNotExposeProviderDetailsOrSecrets() {
    var source =
        new IamTokenDataSource(
            URL,
            "postgres",
            () -> {
              throw new IllegalStateException("secret-must-not-appear");
            });
    assertThatThrownBy(source::getConnection)
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("Sign in to AWS")
        .hasMessageNotContaining("secret-must-not-appear")
        .hasNoCause();
  }

  @Test
  void credentialsCannotBeOverriddenByPoolBorrowers() {
    var source = new IamTokenDataSource(URL, "postgres", () -> "token");
    assertThatThrownBy(() -> source.getConnection("other", "password"))
        .isInstanceOf(SQLFeatureNotSupportedException.class);
  }

  @Test
  void tokenIsRemovedEvenWhenConnectionFails() {
    List<Properties> retained = new ArrayList<>();
    var source =
        new IamTokenDataSource(
            URL,
            "postgres",
            () -> "token",
            (url, p) -> {
              retained.add(p);
              throw new SQLException("Connection failed");
            });
    assertThatThrownBy(source::getConnection).isInstanceOf(SQLException.class);
    assertThat(retained.getFirst()).doesNotContainKey("password");
  }

  @Test
  void endpointKeepsActualAwsHostnameAndPort() {
    assertThat(IamEndpoint.from(URL)).isEqualTo(new IamEndpoint(HOST, 5432));
  }

  @Test
  void insecureAmbiguousAndCredentialBearingUrlsAreRejected() {
    for (String url :
        List.of(
            "jdbc:h2:mem:test",
            "jdbc:postgresql://localhost/yfc?sslmode=verify-full&sslrootcert=/tmp/ca.pem",
            URL.replace("verify-full", "require"),
            URL + "&sslmode=disable",
            URL + "&password=secret",
            URL + "&sslfactory=custom",
            URL + "&sslhostnameverifier=custom",
            URL.replace("/yfc?", "/?"))) {
      assertThatThrownBy(() -> IamEndpoint.from(url)).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
