package com.nitditto.mapper;

import com.nitditto.model.FlightCabinAncillary;
import com.nitditto.payload.response.AncillaryResponse;
import com.nitditto.payload.response.FlightCabinAncillaryResponse;

public class FlightCabinAncillaryMapper {

  private FlightCabinAncillaryMapper() {
  }

  // AncillaryResponse (kèm coverages) do service dựng sẵn rồi truyền vào — mapper thuần, không đụng DB.
  public static FlightCabinAncillaryResponse toResponse(final FlightCabinAncillary entity,
      final AncillaryResponse ancillaryResponse) {
    if (entity == null) {
      return null;
    }
    return FlightCabinAncillaryResponse.builder()
        .id(entity.getId())
        .flightId(entity.getFlightId())
        .cabinClassId(entity.getCabinClassId())
        .ancillaryId(entity.getAncillaryId())
        .ancillaryResponse(ancillaryResponse)
        .available(entity.getAvailable())
        .maxQuantity(entity.getMaxQuantity())
        .price(entity.getPrice())
        .includedInFare(entity.getIncludedInFare())
        .build();
  }
}
