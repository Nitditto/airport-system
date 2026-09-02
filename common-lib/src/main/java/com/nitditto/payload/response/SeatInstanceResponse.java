package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import com.nitditto.enums.SeatAvailabilityStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatInstanceResponse {
    private UUID id;

    private UUID flightId;
    private UUID flightInstanceId;
    private UUID flightInstanceCabinId;

    private UUID seatId;
    private SeatResponse seat;

    private SeatAvailabilityStatus status;
    private Boolean isBooked;
    private Boolean isAvailable;
    private Boolean isOccupied;

    private Double fare;
    private Double premiumSuperCharge;

    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
