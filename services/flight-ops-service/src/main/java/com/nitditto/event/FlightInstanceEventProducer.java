package com.nitditto.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Đẩy FlightInstanceCreatedEvent lên Kafka SAU KHI transaction tạo flight instance commit. Nếu gửi ngay trong
 * transaction mà sau đó rollback, seat-service sẽ sinh ghế cho một flight instance không tồn tại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightInstanceEventProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onFlightInstanceCreated(final FlightInstanceCreatedEvent event) {
    final String key = String.valueOf(event.getFlightInstanceId());
    this.kafkaTemplate.send(KafkaTopics.FLIGHT_INSTANCE_CREATED, key, event)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            log.error("Failed to publish FlightInstanceCreatedEvent for flightInstanceId={}", key, ex);
          } else {
            log.debug("Published FlightInstanceCreatedEvent for flightInstanceId={} at offset {}", key,
                result.getRecordMetadata().offset());
          }
        });
  }
}
