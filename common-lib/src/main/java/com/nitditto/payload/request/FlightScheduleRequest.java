package com.nitditto.payload.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightScheduleRequest {

  @NotNull(message = "Flight ID is required")
  private Long flightId;

  @NotNull(message = "Departure time is required")
  private LocalTime departureTime;

  @NotNull(message = "Arrival time is required")
  private LocalTime arrivalTime;

  @NotNull(message = "Start date is required")
  private LocalDate startDate;

  @NotNull(message = "End date is required")
  private LocalDate endDate;

  @NotEmpty(message = "Operating days cannot be empty")
  private List<DayOfWeek> operatingDays;

  private Boolean isActive;
}
