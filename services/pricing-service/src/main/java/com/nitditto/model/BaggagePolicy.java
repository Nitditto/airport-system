package com.nitditto.model;

import com.nitditto.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(
        name = "baggage_policies",
        indexes = @Index(name = "idx_baggage_policies_airline", columnList = "airline_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggagePolicy extends BaseEntity {

    @Column(name = "fare_id", columnDefinition = "binary(16)", nullable = false, unique = true)
    private UUID fareId;

    @Column(nullable = false)
    private String name;

    private String description;

    private Double cabinBaggageMaxWeight;
    private Integer cabinBaggagePieces = 1;
    private Double cabinBaggageWeightPerPiece;
    private Integer cabinBaggageMaxDimension;

    private Double checkInBaggageMaxWeight;
    private Integer checkInBaggagePieces = 1;

    private Double checkInBaggageWeightPerPiece;

    private Integer freeCheckedBagsAllowance = 0;
    private Boolean priorityBaggage = false;

    private Boolean extraBaggageAllowance = false;

    @Column(name = "airline_id", columnDefinition = "binary(16)")
    private UUID airlineId;

}
