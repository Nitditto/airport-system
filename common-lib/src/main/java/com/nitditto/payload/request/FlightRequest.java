package com.nitditto.payload.request;

import com.nitditto.enums.FlightStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightRequest {

  @NotBlank(message = "Flight number is required")
  @Size(max = 10)
  private String flightNumber;

  private UUID airlineId;

  @NotNull(message = "Aircraft ID is required")
  private UUID aircraftId;

  @NotNull(message = "Departure airport ID is required")
  private UUID departureAirportId;

  @NotNull(message = "Arrival airport ID is required")
  private UUID arrivalAirportId;

  private FlightStatus status;
}
