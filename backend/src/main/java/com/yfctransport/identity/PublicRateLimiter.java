package com.yfctransport.identity;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounded, thread-safe per-IP rate limiter for unauthenticated public and authentication endpoints.
 */
@Component
class PublicRateLimiter {
  private static final int MAX_BUCKETS = 10_000;
  private static final long WINDOW_SECONDS = 60;

  private final boolean enabled;
  private final boolean trustProxy;
  private final int loginPerMinute;
  private final int authPerMinute;
  private final int publicWritePerMinute;
  private final int publicReadPerMinute;
  private final ConcurrentHashMap<String, WindowCounter> buckets = new ConcurrentHashMap<>();

  PublicRateLimiter(
      @Value("${yfc.rate-limit.enabled:true}") boolean enabled,
      @Value("${yfc.rate-limit.trust-proxy:false}") boolean trustProxy,
      @Value("${yfc.rate-limit.login-per-minute:25}") int loginPerMinute,
      @Value("${yfc.rate-limit.auth-per-minute:10}") int authPerMinute,
      @Value("${yfc.rate-limit.public-write-per-minute:15}") int publicWritePerMinute,
      @Value("${yfc.rate-limit.public-read-per-minute:120}") int publicReadPerMinute) {
    this.enabled = enabled;
    this.trustProxy = trustProxy;
    this.loginPerMinute = Math.max(1, loginPerMinute);
    this.authPerMinute = Math.max(1, authPerMinute);
    this.publicWritePerMinute = Math.max(1, publicWritePerMinute);
    this.publicReadPerMinute = Math.max(1, publicReadPerMinute);
  }

  record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {}

  boolean isEnabled() {
    return enabled;
  }

  boolean isTrustProxy() {
    return trustProxy;
  }

  Decision check(String bucketKey, int limit) {
    if (!enabled) return new Decision(true, limit, limit, 0);
    long nowSec = Instant.now().getEpochSecond();
    long window = nowSec / WINDOW_SECONDS;
    if (buckets.size() > MAX_BUCKETS) {
      buckets.entrySet().removeIf(e -> e.getValue().window < window);
      if (buckets.size() > MAX_BUCKETS) buckets.clear();
    }
    WindowCounter counter =
        buckets.compute(
            bucketKey,
            (k, existing) -> {
              if (existing == null || existing.window != window) return new WindowCounter(window);
              return existing;
            });
    int count = counter.count.incrementAndGet();
    long retryAfter = Math.max(1, ((window + 1) * WINDOW_SECONDS) - nowSec);
    if (count > limit) {
      return new Decision(false, limit, 0, retryAfter);
    }
    return new Decision(true, limit, Math.max(0, limit - count), 0);
  }

  int loginLimit() {
    return loginPerMinute;
  }

  int authLimit() {
    return authPerMinute;
  }

  int publicWriteLimit() {
    return publicWritePerMinute;
  }

  int publicReadLimit() {
    return publicReadPerMinute;
  }

  void clear() {
    buckets.clear();
  }

  private static final class WindowCounter {
    final long window;
    final AtomicInteger count = new AtomicInteger(0);

    WindowCounter(long window) {
      this.window = window;
    }
  }
}
