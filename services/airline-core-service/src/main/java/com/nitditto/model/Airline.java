package com.nitditto.model;

import com.nitditto.embeddable.Support;
import com.nitditto.enums.AirlineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
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
@EntityListeners({AuditingEntityListener.class})
public class Airline {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String iataCode;

  @Column(unique = true, nullable = false)
  private String icaoCode;

  @Column(nullable = false)
  private Long ownerId;

  @Column(nullable = false)
  private String name;

  private String alias;

  private String logoUrl;
  private String website;

  @Enumerated(EnumType.STRING)
  private AirlineStatus status = AirlineStatus.ACTIVE;

  private String alliance;

  private Long headquartersCityId;

  @Embedded
  private Support support;

  private Long updatedById;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;
}
