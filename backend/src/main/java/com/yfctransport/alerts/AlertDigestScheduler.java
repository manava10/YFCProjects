package com.yfctransport.alerts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "yfc.alerts.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
class AlertDigestScheduler {
  private final AlertDigestService alerts;

  AlertDigestScheduler(AlertDigestService alerts) {
    this.alerts = alerts;
  }

  @Scheduled(cron = "${yfc.alerts.digest-cron:0 0 8 * * *}", zone = "Asia/Kolkata")
  void runDailyDigest() {
    alerts.runDigest("SCHEDULER");
  }
}
