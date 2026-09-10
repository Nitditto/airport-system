package com.nitditto.payload.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightCabinAncillaryRequest {

    @NotNull(message = "Flight ID is required")
    private UUID flightId;

    @NotNull(message = "Cabin class ID is required")
    private UUID cabinClassId;

    @NotNull(message = "Ancillary ID is required")
    private UUID ancillaryId;

    @NotNull(message = "Available status is required")
    private Boolean available;

    @Positive(message = "Max quantity must be positive")
    private Integer maxQuantity;

    @PositiveOrZero(message = "Price must be zero or positive")
    private Double price;

    @NotNull(message = "Included in fare status is required")
    private Boolean includedInFare;
}
