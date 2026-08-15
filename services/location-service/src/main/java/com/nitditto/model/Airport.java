package com.nitditto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nitditto.embeddable.Address;
import com.nitditto.embeddable.GeoCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
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
public class Airport {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

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

  @ManyToOne
  @JsonIgnore
  private City city;

  @JsonIgnore
  @Transient
  public String getDetailedName() {
    if (city != null && city.getCountryCode() != null) {
      return name.toUpperCase() + "/" + city.getCityCode();
    }
    return name.toUpperCase();
  }
}
