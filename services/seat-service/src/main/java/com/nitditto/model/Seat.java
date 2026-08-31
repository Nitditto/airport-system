package com.nitditto.model;

import java.util.UUID;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.SeatType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seats", indexes = {
        @Index(name = "idx_seat_seat_map", columnList = "seat_map_id"),
        @Index(name = "idx_seat_cabin_class", columnList = "cabin_class_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_seat_map_seat_number", columnNames = {"seat_map_id", "seat_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat extends BaseEntity {

    @Column(nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    private Integer seatRow;

    private Character columnLetter;

    @Enumerated(EnumType.STRING)
    private SeatType seatType;

    private Double basePrice;

    private Double premiumSuperCharge;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isAvailable = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isBooked = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isEmergencyExit = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean hasExtraLegroom = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean hasPowerOutlet = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean hasTvScreen = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean hasExtraWidth = false;

    private Integer seatPitch;

    private Integer seatWidth;

    private Integer reclineAngle;

    @Column(name = "seat_map_id", columnDefinition = "binary(16)", nullable = false)
    private UUID seatMapId;

    @Column(name = "cabin_class_id", columnDefinition = "binary(16)", nullable = false)
    private UUID cabinClassId;

    @Version
    @Column(name = "version")
    private Long version;

    public Double getTotalPrice() {
        final double base = this.basePrice != null ? this.basePrice : 0.0;
        final double surcharge = this.premiumSuperCharge != null ? this.premiumSuperCharge : 0.0;
        return base + surcharge;
    }

    public boolean isBookable() {
        return Boolean.TRUE.equals(this.isActive)
                && Boolean.TRUE.equals(this.isAvailable)
                && !Boolean.TRUE.equals(this.isBooked);
    }

    public String getFullPosition() {
        return this.seatRow + " " + this.columnLetter;
    }

    public boolean isPremiumSeat() {
        return this.premiumSuperCharge != null && this.premiumSuperCharge > 0;
    }

    public String getSeatCharacteristics() {
        final StringBuilder characteristics = new StringBuilder();
        if (Boolean.TRUE.equals(this.hasExtraLegroom)) {
            characteristics.append("Extra Legroom, ");
        }
        if (Boolean.TRUE.equals(this.hasExtraWidth)) {
            characteristics.append("Extra Width, ");
        }
        if (Boolean.TRUE.equals(this.hasPowerOutlet)) {
            characteristics.append("Power Outlet, ");
        }
        if (Boolean.TRUE.equals(this.hasTvScreen)) {
            characteristics.append("TV Screen, ");
        }
        if (Boolean.TRUE.equals(this.isEmergencyExit)) {
            characteristics.append("Emergency Exit, ");
        }
        if (characteristics.isEmpty()) {
            return "Standard";
        }
        return characteristics.substring(0, characteristics.length() - 2);
    }
}
