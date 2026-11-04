package com.nitditto.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FlightInstanceCreatedEvent {

  private UUID flightInstanceId;
  private UUID aircraftId;
  private UUID flightId;

}
