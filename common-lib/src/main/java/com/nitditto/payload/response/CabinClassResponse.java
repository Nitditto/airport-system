package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinClassResponse {

    private UUID id;
    private String name;
    private String code;
    private String description;
    private UUID aircraftId;
    private Integer displayOrder;
    private Boolean isActive;
    private Boolean isBookable;
    private Integer typicalSeatPitch;
    private Integer typicalSeatWidth;
    private String seatType;
    private Instant createdAt;
    private Instant updatedAt;
    private SeatMapResponse seatMap;

}
