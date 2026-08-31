package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapResponse {
    private UUID id;

    private String name;

    private Integer totalRows;

    private UUID airlineId;
    private String airlineName;
    private String airlineCode;

    private UUID cabinClassId;
    private String cabinClassName;
    private String cabinClassCode;

    private Integer totalSeats;
    private Integer availableSeats;
    private Integer occupiedSeats;

    private List<SeatResponse> seats;

    private Integer windowSeats;
    private Integer aisleSeats;
    private Integer middleSeats;
    private Integer premiumSeats;
    private Integer emergencyExitSeats;

    private Integer leftSeatsPerRow;
    private Integer rightSeatsPerRow;

}
