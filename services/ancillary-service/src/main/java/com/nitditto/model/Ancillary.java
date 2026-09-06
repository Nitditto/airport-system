package com.nitditto.model;

import java.util.UUID;

import com.nitditto.domain.AncillaryMetadata;
import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.AncillaryType;
import com.nitditto.service.AncillaryMetadataConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ancillary")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ancillary extends BaseEntity {

  @Enumerated(EnumType.STRING)
  private AncillaryType type;

  private String subType;
  private String rfisc;

  @Column(nullable = false)
  private String name;
  private String description;

  @Convert(converter = AncillaryMetadataConverter.class)
  private AncillaryMetadata metadata;

  private Integer displayOrder;
  private UUID airlineId;
}
