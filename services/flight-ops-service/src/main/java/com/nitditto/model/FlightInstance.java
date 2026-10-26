package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.FlightStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "flight_instances",
        indexes = {
                @Index(name = "idx_flight_instance_airline_departure",
                        columnList = "airline_id, departure_date_time"),
                @Index(name = "idx_flight_instance_flight", columnList = "flight_id"),
                @Index(name = "idx_flight_instance_route_departure",
                        columnList = "departure_airport_id, arrival_airport_id, departure_date_time")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightInstance extends BaseEntity {

  @Column(name = "airline_id", columnDefinition = "binary(16)")
  private UUID airlineId;

  @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
  private UUID flightId;

  @Column(name = "departure_airport_id", columnDefinition = "binary(16)", nullable = false)
  private UUID departureAirportId;

  @Column(name = "arrival_airport_id", columnDefinition = "binary(16)", nullable = false)
  private UUID arrivalAirportId;

  @Column(name = "schedule_id", columnDefinition = "binary(16)", nullable = false)
  private UUID scheduleId;

  private LocalDateTime departureDateTime;

  private LocalDateTime arrivalDateTime;

  @Column(nullable = false)
  private Integer totalSeats;

  @Column(nullable = false)
  private Integer availableSeats;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private FlightStatus status;

  private Integer minAdvanceBookingDays;
  private Integer maxAdvanceBookingDays;

  private Boolean isActive = true;

  @Transient
  public String getFormatedDuration() {
    if (this.departureDateTime == null || this.arrivalDateTime == null) {
      return null;
    }
    final Duration duration = Duration.between(this.departureDateTime, this.arrivalDateTime);
    final long hours = duration.toHours();
    final long minutes = duration.toMinutes() % 60;

    final StringBuilder sb = new StringBuilder();
    if (hours > 0) {
      sb.append(hours).append("h ");
    }
    if (minutes > 0) {
      sb.append(minutes).append("min ");
    }
    return sb.toString().trim();
  }
}
