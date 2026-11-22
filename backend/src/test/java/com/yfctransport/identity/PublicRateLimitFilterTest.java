package com.yfctransport.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PublicRateLimitFilterTest {

  @Test
  void enforcesPerIpRateLimitAndReturns429WithRetryAfter() throws Exception {
    var limiter = new PublicRateLimiter(true, false, 2, 2, 2, 3);
    var filter = new PublicRateLimitFilter(limiter);

    for (int i = 0; i < 2; i++) {
      var req = new MockHttpServletRequest("POST", "/api/v1/site/enquiries");
      req.setRemoteAddr("203.0.113.10");
      var res = new MockHttpServletResponse();
      filter.doFilter(req, res, new MockFilterChain());
      assertThat(res.getStatus()).isEqualTo(200);
      assertThat(res.getHeader("X-RateLimit-Limit")).isEqualTo("2");
    }

    // 3rd request from same IP in same minute must be blocked with 429
    var blockedReq = new MockHttpServletRequest("POST", "/api/v1/site/enquiries");
    blockedReq.setRemoteAddr("203.0.113.10");
    var blockedRes = new MockHttpServletResponse();
    filter.doFilter(blockedReq, blockedRes, new MockFilterChain());
    assertThat(blockedRes.getStatus()).isEqualTo(429);
    assertThat(blockedRes.getHeader("Retry-After")).isNotBlank();
    assertThat(blockedRes.getContentAsString()).contains("Too Many Requests");

    // Different IP is still allowed independently
    var otherIpReq = new MockHttpServletRequest("POST", "/api/v1/site/enquiries");
    otherIpReq.setRemoteAddr("203.0.113.11");
    var otherIpRes = new MockHttpServletResponse();
    filter.doFilter(otherIpReq, otherIpRes, new MockFilterChain());
    assertThat(otherIpRes.getStatus()).isEqualTo(200);
  }
}
