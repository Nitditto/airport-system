package com.nitditto.payload.response;

import com.nitditto.enums.AircraftStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NotNull
public class AircraftResponse {

  private UUID id;
  private String code;
  private String model;
  private String manufacturer;
  private Integer seatingCapacity;
  private Integer economySeats;
  private Integer premiumEconomySeats;
  private Integer businessSeats;
  private Integer firstClassSeats;
  private Integer rangeKm;
  private Integer cruisingSpeedKmh;
  private Integer maxAltitudeFt;
  private Integer yearOfManufacture;
  private LocalDate registrationDate;
  private LocalDate nextMaintenanceDate;
  private AircraftStatus status;
  private Boolean isAvailable;

  private UUID airlineId;
  private String airlineName;
  private String airlineIataCode;

  private UUID currentAirportId;
  private UUID currentAirportCity;
  private String currentAirportCode;
  private String currentAirportName;

  private Integer totalSeats;
  private Boolean requiresMaintenance;
  private Boolean isOperational;

  private Instant createdAt;
  private Instant updatedAt;
}
