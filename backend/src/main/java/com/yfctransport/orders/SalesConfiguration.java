package com.yfctransport.orders;

import java.time.*;
import org.springframework.context.annotation.*;

@Configuration
class SalesConfiguration {
  @Bean
  Clock salesClock() {
    return Clock.system(ZoneId.of("Asia/Kolkata"));
  }
}
