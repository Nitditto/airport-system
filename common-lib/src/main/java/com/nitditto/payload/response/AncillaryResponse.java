package com.nitditto.payload.response;

import java.util.List;
import java.util.UUID;

import com.nitditto.domain.AncillaryMetadata;
import com.nitditto.enums.AncillaryType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AncillaryResponse {

  private UUID id;

  private AncillaryType type;

  private String subType;
  private String rfisc;

  private String name;
  private String description;

  private String categoryDisplayName;
  private String iconUrl;

  private AncillaryMetadata metadata;
  private List<InsuranceCoverageResponse> coverages;
  private Integer displayOrder;
  private UUID airlineId;

}
