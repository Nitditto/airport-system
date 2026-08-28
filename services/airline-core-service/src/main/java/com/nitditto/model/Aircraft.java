package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.AircraftStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "aircrafts",
        indexes = @Index(name = "idx_aircraft_airline", columnList = "airline_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Aircraft extends BaseEntity {

  @Column(unique = true, nullable = false)
  private String code;

  @Column(nullable = false)
  private String model;

  @Column(nullable = false, length = 50)
  private String manufacturer;

  @Column(nullable = false)
  private Integer seatingCapacity;

  @Column(name = "economy_seats")
  private Integer economySeats = 0;

  @Column(name = "premium_economy_seats")
  private Integer premiumEconomySeats = 0;

  @Column(name = "business_seats")
  private Integer businessSeats = 0;

  @Column(name = "first_class_seats")
  private Integer firstClassSeats = 0;

  private Integer rangeKm;

  @Column(name = "cruising_speed_kmh")
  private Integer cruisingSpeedKmh;

  private Integer maxAltitudeFt;

  @Column(name = "year_of_manufacture")
  private Integer yearOfManufacture;

  private LocalDate registrationDate;
  private LocalDate nextMaintenanceDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AircraftStatus status = AircraftStatus.ACTIVE;

  private Boolean isAvailable = true;

  @Column(name = "airline_id", columnDefinition = "binary(16)", nullable = false)
  private UUID airlineId;

  @Column(name = "current_airport_id", columnDefinition = "binary(16)")
  private UUID currentAirportId;

  public Integer getTotalSeats() {
    final int economy = this.economySeats != null ? this.economySeats : 0;
    final int premiumEconomy = this.premiumEconomySeats != null ? this.premiumEconomySeats : 0;
    final int business = this.businessSeats != null ? this.businessSeats : 0;
    final int firstClass = this.firstClassSeats != null ? this.firstClassSeats : 0;
    return economy + premiumEconomy + business + firstClass;
  }

  public boolean isOperational() {
    return AircraftStatus.ACTIVE.equals(this.status) && Boolean.TRUE.equals(this.isAvailable);
  }

  public boolean requiresMaintenance() {
    return this.nextMaintenanceDate != null
        && this.nextMaintenanceDate.isBefore(LocalDate.now().plusWeeks(2));
  }

}
