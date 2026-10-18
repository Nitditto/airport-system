package com.nitditto.model;

import java.util.UUID;

import com.nitditto.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "flight_cabin_ancillary", uniqueConstraints = {
    @UniqueConstraint(name = "uk_fca_flight_cabin_ancillary",
        columnNames = { "flight_id", "cabin_class_id", "ancillary_id" }) },
    indexes = { @Index(name = "idx_fca_ancillary", columnList = "ancillary_id") })
@Entity
public class FlightCabinAncillary extends BaseEntity {

  @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
  private UUID flightId;

  @Column(name = "cabin_class_id", columnDefinition = "binary(16)", nullable = false)
  private UUID cabinClassId;

  @Column(name = "ancillary_id", columnDefinition = "binary(16)", nullable = false)
  private UUID ancillaryId;

  @Builder.Default
  private Boolean available = true;

  private Integer maxQuantity;

  private Double price;

  @Builder.Default
  private Boolean includedInFare = false;

}
