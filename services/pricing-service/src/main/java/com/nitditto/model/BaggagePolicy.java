package com.nitditto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggagePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne
    @JsonIgnore
    private Fare fare;

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

    private Long airlineId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;


}
