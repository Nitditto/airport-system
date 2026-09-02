package com.nitditto.payload.response;

import java.time.Instant;
import java.util.UUID;

import com.nitditto.enums.SeatType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {

    private UUID id;
    private String seatNumber;
    private Integer seatRow;
    private Character columnLetter;
    private SeatType seatType;

    private Boolean isAvailable;
    private Boolean isBooked;
    private Boolean isEmergencyExit;
    private Boolean isActive;

    private Double basePrice;
    private Double premiumSuperCharge;
    private Double totalPrice;

    private Boolean hasExtraLegroom;
    private Boolean hasPowerOutlet;
    private Boolean hasTvScreen;
    private Boolean hasExtraWidth;

    private Integer seatPitch;
    private Integer seatWidth;
    private Integer reclineAngle;

    private UUID seatMapId;
    private String seatMapName;
    private UUID cabinClassId;
    private String cabinClassName;

    private Instant createdAt;
    private Instant updatedAt;

    private Boolean isPremiumSeat;
    private Boolean isBookable;
    private String fullPosition;
    private String seatCharacteristics;

}
