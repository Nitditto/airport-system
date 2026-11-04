package com.nitditto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;

/**
 * Consumer factory, deserializer (ErrorHandlingDeserializer + JsonDeserializer) và trusted packages do Spring Boot tạo
 * từ {@code spring.kafka.consumer.*} trong application.yaml; ở đây chỉ bổ sung error handler. Boot tự gắn bean
 * CommonErrorHandler vào listener container factory mặc định.
 */
@Configuration
public class KafkaConsumerConfig {

  @Bean
  public CommonErrorHandler kafkaErrorHandler() {
    // Lỗi tạm thời (service khác down, timeout Feign...) thử lại 3 lần, cách nhau 2s; hết lượt thì log lỗi và bỏ qua
    // message. Lỗi nghiệp vụ thì thử lại cũng vô ích nên không retry.
    final DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(2000L, 3L));
    handler.addNotRetryableExceptions(BusinessException.class, ResourceNotFoundException.class);
    return handler;
  }
}
