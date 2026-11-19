package com.nitditto.payload.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event: flight-ops tạo xong một flight instance (topic {@code kafka.topics.flight-instance-created},
 * key = {@code flightInstanceId}). seat-service nghe để sinh cabin + ghế.
 */
public record FlightInstanceCreatedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID flightInstanceId,
    UUID aircraftId,
    UUID flightId) {

  public static FlightInstanceCreatedEvent of(final UUID flightInstanceId, final UUID aircraftId,
      final UUID flightId) {
    return new FlightInstanceCreatedEvent(EventIds.newEventId(), Instant.now(), flightInstanceId, aircraftId, flightId);
  }
}
