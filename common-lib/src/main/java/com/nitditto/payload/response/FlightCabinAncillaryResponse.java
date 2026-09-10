package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightCabinAncillaryResponse {

    private UUID id;
    private UUID flightId;
    private UUID cabinClassId;
    private UUID ancillaryId;
    private AncillaryResponse ancillaryResponse;
    private Boolean available;
    private Integer maxQuantity;
    private Double price;
    private Boolean includedInFare;
}
