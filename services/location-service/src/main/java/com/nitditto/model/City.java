package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
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
@Table(name = "cities")
public class City extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String cityCode;

  @Column(nullable = false)
  private String countryCode;

  @Column(nullable = false)
  private String countryName;

  @Size(max = 10)
  private String regionCode;

  @Column(name = "time_zone_id", length = 50)
  private String timeZoneId;
}
