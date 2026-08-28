package com.nitditto.model;

import com.nitditto.converter.DayOfWeekSetConverter;
import com.nitditto.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flight_schedules")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlightSchedule extends BaseEntity {

  @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
  private UUID flightId;

  @Column(name = "departure_airport_id", columnDefinition = "binary(16)", nullable = false)
  private UUID departureAirportId;

  @Column(name = "arrival_airport_id", columnDefinition = "binary(16)", nullable = false)
  private UUID arrivalAirportId;

  @Column(nullable = false)
  private LocalTime departureTime;

  @Column(nullable = false)
  private LocalTime arrivalTime;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @Convert(converter = DayOfWeekSetConverter.class)
  @Column(name = "operating_days", length = 32)
  @Builder.Default
  private Set<DayOfWeek> operatingDays = new HashSet<>();

  private Boolean isActive = true;
}
