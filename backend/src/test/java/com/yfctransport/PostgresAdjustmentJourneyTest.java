package com.yfctransport;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "YFC_TEST_POSTGRES_URL", matches = ".+")
class PostgresAdjustmentJourneyTest extends AdjustmentJourneyTest {
  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add(
        "yfc.database.auth-mode",
        () -> System.getenv().getOrDefault("YFC_TEST_DB_AUTH_MODE", "password"));
    registry.add("yfc.database.aws-region", () -> System.getenv().getOrDefault("AWS_REGION", ""));
    registry.add("spring.datasource.url", () -> System.getenv("YFC_TEST_POSTGRES_URL"));
    registry.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("YFC_TEST_POSTGRES_USER", "yfc"));
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("YFC_TEST_POSTGRES_PASSWORD", ""));
  }
}
