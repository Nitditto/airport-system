package com.nitditto.model;

import com.nitditto.embeddable.BoardingBenefits;
import com.nitditto.embeddable.FlexibilityBenefits;
import com.nitditto.embeddable.InFlightBenefits;
import com.nitditto.embeddable.PremiumServiceBenefits;
import com.nitditto.embeddable.SeatBenefits;
import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.CabinClassType;
import jakarta.persistence.*;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "fares",
        indexes = @Index(name = "idx_fare_flight_cabin", columnList = "flight_id, cabin_class_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fare extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Character rbdCode;

    @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightId;

    @Column(name = "cabin_class_id", columnDefinition = "binary(16)", nullable = false)
    private UUID cabinClassId;

    @Enumerated(EnumType.STRING)
    private CabinClassType cabinClass;

    @Column(nullable = false)
    private Double baseFare;

    private Double taxedAndFees;
    private Double airlineFees;

    @Column(nullable = false)
    private Double currentPrice;

    private String fareLabel;

    @Embedded
    private SeatBenefits seatBenefits = new SeatBenefits();

    @Embedded
    private BoardingBenefits boardingBenefits = new BoardingBenefits();

    @Embedded
    @Builder.Default
    private InFlightBenefits inFlightBenefits = new InFlightBenefits();

    @Embedded
    @Builder.Default
    private FlexibilityBenefits flexibilityBenefits = new FlexibilityBenefits();

    @Embedded
    @Builder.Default
    private PremiumServiceBenefits premiumServiceBenefits = new PremiumServiceBenefits();

    public Double getTotalPrice() {
        final double base = this.baseFare != null ? this.baseFare : 0.0;
        final double taxes = this.taxedAndFees != null ? this.taxedAndFees : 0.0;
        final double fees = this.airlineFees != null ? this.airlineFees : 0.0;
        final double current = this.currentPrice != null ? this.currentPrice : 0.0;
        return base + taxes + fees + current;
    }
}
