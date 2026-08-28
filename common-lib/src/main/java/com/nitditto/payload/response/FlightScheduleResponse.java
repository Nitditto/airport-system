package com.nitditto.payload.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlightScheduleResponse {

  private UUID id;
  private UUID flightId;
  private String flightNumber;

  private AirportResponse departureAirport;
  private AirportResponse arrivalAirport;

  private LocalTime departureTime;
  private LocalTime arrivalTime;

  private LocalDate startDate;
  private LocalDate endDate;

  private List<DayOfWeek> operatingDays;

  private Boolean isActive;
}
