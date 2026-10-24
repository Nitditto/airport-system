package com.nitditto.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  // Inject Clock (thay vì Instant.now()) để kiểm thử logic hết hạn giữ chỗ mà không phải chờ thật.
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
