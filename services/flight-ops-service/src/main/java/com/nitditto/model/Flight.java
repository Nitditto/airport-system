package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.FlightStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "flights",
        indexes = @Index(name = "idx_flight_airline_airports",
                columnList = "airline_id, departure_airport_id, arrival_airport_id")
)
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Flight extends BaseEntity {

  @Column(nullable = false, unique = true)
  private String flightNumber;

  @Column(name = "airline_id", columnDefinition = "binary(16)", nullable = false)
  private UUID airlineId;

  @Column(name = "aircraft_id", columnDefinition = "binary(16)", nullable = false)
  private UUID aircraftId;

  @Column(name = "departure_airport_id", columnDefinition = "binary(16)", nullable = false)
  private UUID departureAirportId;

  @Column(name = "arrival_airport_id", columnDefinition = "binary(16)", nullable = false)
  private UUID arrivalAirportId;

  @Builder.Default
  private FlightStatus status = FlightStatus.SCHEDULED;
}
