package com.nitditto.model;

import com.nitditto.enums.FlightStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class FlightInstance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long airlineId;

  @ManyToOne
  private Flight flight;

  @Column(nullable = false)
  private Long departureAirportId;

  @Column(nullable = false)
  private Long arrivalAirportId;

  @Column(nullable = false)
  private Long scheduleId;

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
    if (departureDateTime == null || arrivalDateTime == null) {
      return null;
    }
    Duration duration = Duration.between(departureDateTime, arrivalDateTime);
    long hours = duration.toHours();
    long minutes = duration.toMinutes() % 60;

    StringBuilder sb = new StringBuilder();
    if (hours > 0) {
      sb.append(hours).append("h ");
    }
    if (minutes > 0) {
      sb.append(minutes).append("min ");
    }
    return sb.toString().trim();
  }
}
