package com.nitditto.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.nitditto.event.KafkaTopics;

@Configuration
public class KafkaProducerConfig {

  // Producer sở hữu topic của mình. 3 partition để consumer chạy song song (spring.kafka.listener.concurrency=3);
  // key = bookingId / flightInstanceId nên thứ tự event của cùng một đối tượng vẫn được giữ.
  private static final int TOPIC_PARTITIONS = 3;
  // Broker hiện chỉ có 1 node; tăng lên khi chạy cluster.
  private static final int TOPIC_REPLICAS = 1;

  @Value("${spring.kafka.bootstrap-servers:${kafka.bootstrap-servers:localhost:9092}}")
  private String bootstrapServers;

  @Bean
  public ProducerFactory<String, Object> producerFactory() {
    final Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    // Producer chạy trên thread request (listener AFTER_COMMIT): broker chết thì send() chỉ được chặn tối đa 3s
    // (mặc định 60s) để không treo API của người dùng.
    config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
    config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate() {
    return new KafkaTemplate<>(this.producerFactory());
  }

  @Bean
  public NewTopic flightInstanceCreatedTopic() {
    return TopicBuilder.name(KafkaTopics.FLIGHT_INSTANCE_CREATED).partitions(TOPIC_PARTITIONS).replicas(TOPIC_REPLICAS).build();
  }
}
