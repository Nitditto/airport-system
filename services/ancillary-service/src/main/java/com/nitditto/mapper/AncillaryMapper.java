package com.nitditto.mapper;

import java.util.List;

import com.nitditto.model.Ancillary;
import com.nitditto.payload.response.AncillaryResponse;
import com.nitditto.payload.response.InsuranceCoverageResponse;

public class AncillaryMapper {

  public static AncillaryResponse toResponse(Ancillary ancillary,
      List<InsuranceCoverageResponse> converageResponsesList) {
    if (ancillary == null)
      return null;
    return AncillaryResponse.builder()
        .id(ancillary.getId())
        .type(ancillary.getType())
        .subType(ancillary.getSubType())
        .rfisc(ancillary.getRfisc())
        .name(ancillary.getName())
        .description(ancillary.getDescription())
        .coverages(converageResponsesList)
        .metadata(ancillary.getMetadata())
        .displayOrder(ancillary.getDisplayOrder())
        .airlineId(ancillary.getAirlineId())
        .build();
  }
}
