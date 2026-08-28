package com.nitditto.payload.response;


import com.nitditto.enums.FlightStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlightInstanceResponse {

  private UUID id;

  private UUID flightId;
  private String flightNumber;

  private UUID airlineId;
  private String airlineName;
  private String airlineLogo;
  private UUID aircraftId;
  private String aircraftModal;
  private String aircraftCode;
  private AirportResponse departureAirport;
  private AirportResponse arrivalAirport;

  private LocalDateTime departureDateTime;
  private LocalDateTime arrivalDateTime;
  private String formatedDuration;

  private Integer totalSeats;
  private Integer availableSeats;

  private FlightStatus status;

  private Integer minAdvanceBookingDays;
  private Integer maxAdvanceBookingDays;
  private Boolean isActive;

}
