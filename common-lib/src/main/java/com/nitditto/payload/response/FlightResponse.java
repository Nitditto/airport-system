package com.nitditto.payload.response;

import com.nitditto.enums.FlightStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightResponse {

  private UUID id;
  private String flightNumber;
  private AirlineResponse airline;
  private AircraftResponse aircraft;
  private AirportResponse departureAirport;
  private AirportResponse arrivalAirport;
  private LocalDateTime departureTime;
  private LocalDateTime arrivalTime;
  private FlightStatus status;
  private Double lowestPrice;
  private Integer totalAvailableSeats;
  private Instant createdAt;
  private Instant updatedAt;
}
