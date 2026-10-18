package com.yfctransport.database;

import com.zaxxer.hikari.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;

@Configuration
@ConditionalOnProperty(name = "yfc.database.auth-mode", havingValue = "iam")
class IamDatabaseConfiguration {
  @Bean(destroyMethod = "close")
  DefaultCredentialsProvider databaseCredentials() {
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean(destroyMethod = "close")
  HikariDataSource dataSource(
      DefaultCredentialsProvider credentials,
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${yfc.database.aws-region}") String region,
      @Value("${yfc.database.maximum-pool-size}") int size) {
    if (region.isBlank())
      throw new IllegalArgumentException("Set AWS_REGION for IAM database authentication");
    if (username.isBlank())
      throw new IllegalArgumentException("Set DB_USER for IAM database authentication");
    if (size < 1 || size > 20)
      throw new IllegalArgumentException("DB_POOL_MAX_SIZE must be between 1 and 20");
    IamEndpoint endpoint = IamEndpoint.from(url);
    var utilities =
        RdsUtilities.builder().credentialsProvider(credentials).region(Region.of(region)).build();
    var source =
        new IamTokenDataSource(
            url,
            username,
            () ->
                utilities.generateAuthenticationToken(
                    r -> r.hostname(endpoint.host()).port(endpoint.port()).username(username)));
    HikariConfig config = new HikariConfig();
    config.setDataSource(source);
    config.setMaximumPoolSize(size);
    config.setMinimumIdle(1);
    config.setConnectionTimeout(15000);
    config.setPoolName("yfc-iam-pool");
    return new HikariDataSource(config);
  }
}
