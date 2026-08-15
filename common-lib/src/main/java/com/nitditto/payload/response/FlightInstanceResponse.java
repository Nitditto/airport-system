package com.nitditto.payload.response;


import com.nitditto.enums.FlightStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlightInstanceResponse {

  private Long id;

  private Long flightId;
  private String flightNumber;

  private Long airlineId;
  private String airlineName;
  private String airlineLogo;
  private Long aircraftId;
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
