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
@AllArgsConstructor
@NoArgsConstructor
public class FlightInstanceCabinRequest {

    @NotNull
    private UUID flightId;

    @NotNull(message = "Flight instance ID is required")
    private UUID flightInstanceId;

    @NotNull
    private UUID cabinClassId;

    @NotNull
    @Positive
    private Double baseFare;

    @NotNull
    @PositiveOrZero
    private Double windowSurcharge;

    @NotNull
    @PositiveOrZero
    private Double aisleSurcharge;

    @NotNull
    @PositiveOrZero
    private Double taxesAndFees;

    @NotNull
    @PositiveOrZero
    private Double airlineFees;

    private Double currentPrice;

    private Boolean isActive;


}
