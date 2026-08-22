package com.nitditto.model;

import com.nitditto.embeddable.BoardingBenefits;
import com.nitditto.embeddable.FlexibilityBenefits;
import com.nitditto.embeddable.InFlightBenefits;
import com.nitditto.embeddable.PremiumServiceBenefits;
import com.nitditto.embeddable.SeatBenefits;
import com.nitditto.enums.CabinClassType;
import jakarta.persistence.*;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Fare {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Character rbdCode;

    @Column(nullable = false)
    private Long flightId;

    @Column(nullable = false)
    private Long cabinClassId;

    @Enumerated(EnumType.STRING)
    private CabinClassType cabinClass;

    @Column(nullable = false)
    private Double baseFare;

    private Double taxedAndFees;
    private Double airlineFees;

    @Column(nullable = false)
    private Double currentPrice;

    private String fareLabel;

    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private BaggagePolicy baggagePolicy;

    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private FareRules fareRules;

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

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Double getTotalPrice() {
        double base = baseFare != null ? baseFare : 0.0;
        double taxes = taxedAndFees != null ? taxedAndFees : 0.0;
        double fees = airlineFees != null ? airlineFees : 0.0;
        double current = currentPrice != null ? currentPrice : 0.0;
        return base + taxes + fees + current;
    }
}
