package com.nitditto.payload.request;

import java.util.UUID;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggagePolicyRequest {

    private String name;

    private UUID fareId;

    private String description;

    @PositiveOrZero
    private Double cabinBaggageMaxWeight;

    @PositiveOrZero
    private Integer cabinBaggagePieces;

    @PositiveOrZero
    private Double cabinBaggageWeightPerPiece;

    private Integer cabinBaggageMaxDimension;

    // check-in baggage
    @PositiveOrZero
    private Double checkInBaggageMaxWeight;

    @PositiveOrZero
    private Integer checkInBaggagePieces;

    @PositiveOrZero
    private Double checkInBaggageWeightPerPiece;

    @PositiveOrZero
    private Integer freeCheckedBagsAllowance;

    // benefits
    private Boolean priorityBaggage;
    private Boolean extraBaggageAllowance;
}
