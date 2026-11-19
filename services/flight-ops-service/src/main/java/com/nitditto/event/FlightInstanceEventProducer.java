package com.nitditto.event;

import com.nitditto.payload.event.FlightInstanceCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Đẩy FlightInstanceCreatedEvent lên Kafka SAU KHI transaction tạo flight instance commit. Nếu gửi ngay trong
 * transaction mà sau đó rollback, seat-service sẽ sinh ghế cho một flight instance không tồn tại.
 */
@Slf4j
@Component
public class FlightInstanceEventProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final String topic;

  public FlightInstanceEventProducer(final KafkaTemplate<String, Object> kafkaTemplate,
      @Value("${kafka.topics.flight-instance-created}") final String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onFlightInstanceCreated(final FlightInstanceCreatedEvent event) {
    final String key = String.valueOf(event.flightInstanceId());
    try {
      this.kafkaTemplate.send(this.topic, key, event).whenComplete((result, ex) -> {
        if (ex != null) {
          log.error("Failed to publish FlightInstanceCreatedEvent eventId={} flightInstanceId={}",
              event.eventId(), key, ex);
        } else {
          log.info("Published FlightInstanceCreatedEvent eventId={} flightInstanceId={} to {}-{}@{}", event.eventId(),
              key, this.topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        }
      });
    } catch (final RuntimeException ex) {
      // vd. broker không phản hồi metadata trong max.block.ms
      log.error("Could not send FlightInstanceCreatedEvent eventId={} flightInstanceId={}: {}", event.eventId(), key,
          ex.getMessage(), ex);
    }
  }
}
