package com.nitditto.model;

import com.nitditto.enums.AircraftStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Aircraft {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

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

  @ManyToOne
  private Airline airline;

  private Long currentAirportId;

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Integer getTotalSeats() {
    return economySeats + premiumEconomySeats + businessSeats + firstClassSeats;
  }

  public boolean isOperational() {
    return AircraftStatus.ACTIVE.equals(status) && Boolean.TRUE.equals(isAvailable);
  }

  public boolean requiresMaintenance() {
    return nextMaintenanceDate != null && nextMaintenanceDate.isBefore(
        LocalDate.now().plusWeeks(2));
  }

}
