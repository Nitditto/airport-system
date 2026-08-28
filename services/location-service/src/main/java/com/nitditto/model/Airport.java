package com.nitditto.model;

import com.nitditto.embeddable.Address;
import com.nitditto.embeddable.GeoCode;
import com.nitditto.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "airports")
public class Airport extends BaseEntity {

  @Column(nullable = false, unique = true, length = 3)
  private String iataCode;

  @Column(nullable = false)
  private String name;

  @Embedded
  private Address address;

  @Embedded
  private GeoCode geoCode;

  @Column(name = "time_zone_id", length = 50)
  private String timeZone;

  @Column(name = "city_id", columnDefinition = "binary(16)", nullable = false)
  private UUID cityId;
}
