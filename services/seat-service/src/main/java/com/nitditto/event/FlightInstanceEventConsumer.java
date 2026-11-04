package com.nitditto.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.nitditto.service.FlightInstanceCabinService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sinh cabin + ghế cho flight instance mới. Kafka giao "ít nhất một lần" nên message có thể tới lại (retry,
 * rebalance); generateForFlightInstance bỏ qua cabin đã tạo nên không sinh ghế trùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightInstanceEventConsumer {

  private final FlightInstanceCabinService flightInstanceCabinService;

  @KafkaListener(topics = KafkaTopics.FLIGHT_INSTANCE_CREATED, groupId = "${spring.kafka.consumer.group-id:seat-service-group}")
  public void handleFlightInstanceCreated(final FlightInstanceCreatedEvent event) {
    final int created = this.flightInstanceCabinService.generateForFlightInstance(
        event.getFlightInstanceId(), event.getFlightId(), event.getAircraftId());
    log.info("Created {} seat instances for flightInstanceId={}", created, event.getFlightInstanceId());
  }
}
