package com.nitditto.payload.response;

import java.util.UUID;

import com.nitditto.enums.CoverageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceCoverageResponse {
  private UUID id;
  private UUID ancillaryId;
  private String ancillaryName;
  private CoverageType coverageType;
  private String name;
  private String description;
  private Double coverageAmount;
  private Boolean isFlat;
  private String claimCondition;
  private String emergencyContact;
  private Integer displayOrder;
  private Boolean active;

}
