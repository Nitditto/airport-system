package com.nitditto.model;

import com.nitditto.embeddable.Support;
import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.AirlineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "airlines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Airline extends BaseEntity {

  @Column(unique = true, nullable = false)
  private String iataCode;

  @Column(unique = true, nullable = false)
  private String icaoCode;

  @Column(name = "owner_id", columnDefinition = "binary(16)", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String name;

  private String alias;

  private String logoUrl;
  private String website;

  @Enumerated(EnumType.STRING)
  private AirlineStatus status = AirlineStatus.ACTIVE;

  private String alliance;

  @Column(name = "headquarters_city_id", columnDefinition = "binary(16)")
  private UUID headquartersCityId;

  @Embedded
  private Support support;

  @Column(name = "updated_by_id", columnDefinition = "binary(16)")
  private UUID updatedById;
}
