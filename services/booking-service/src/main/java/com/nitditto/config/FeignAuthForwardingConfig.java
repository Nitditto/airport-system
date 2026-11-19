package com.nitditto.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Chuyển tiếp header {@code Authorization} (JWT người dùng, gateway giữ nguyên khi forward) của request đang xử lý sang
 * mọi lời gọi Feign. Nhờ vậy service đích tự verify JWT (vd. user-service) thấy đúng người dùng ban đầu và áp quyền
 * chính chủ, thay vì nhận request ẩn danh và trả 401.
 *
 * <p>Ngoài request HTTP (Kafka listener, scheduler, AFTER_COMMIT chạy sau khi request đã xong) không có request context
 * nên không gắn gì.
 */
@Configuration
public class FeignAuthForwardingConfig {

  @Bean
  public RequestInterceptor authorizationForwardingInterceptor() {
    return template -> {
      if (!(RequestContextHolder.getRequestAttributes() instanceof final ServletRequestAttributes attributes)) {
        return;
      }
      final String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
      if (authorization != null && !authorization.isBlank()
          && !template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
        template.header(HttpHeaders.AUTHORIZATION, authorization);
      }
    };
  }
}
