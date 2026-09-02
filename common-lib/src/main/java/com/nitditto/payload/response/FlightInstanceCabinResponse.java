package com.nitditto.payload.response;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nitditto.enums.CabinClassType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlightInstanceCabinResponse {
    private UUID id;
    private UUID flightInstanceId;
    private CabinClassType cabinClassType;
    private CabinClassResponse cabinClass;

    // Chỉ layout (SeatMapMapper.toSimpleResponse) -> không lồng trùng list ghế với field
    // `seats` bên dưới (đã có nested SeatResponse trong từng SeatInstanceResponse).
    private SeatMapResponse seatMap;

    // null/rỗng ở list view (getByFlightInstanceId), chỉ populate ở detail view
    // (getFlightInstanceCabinById) -> tránh N+1/payload nặng khi phân trang.
    @Builder.Default
    private List<SeatInstanceResponse> seats = new ArrayList<>();

    private Double baseFare;
    private Double windowSurcharge;
    private Double aisleSurcharge;
    private Double taxesAndFees;
    private Double airlineFees;
    private Double currentPrice;
    private Double totalCabinPrice;

    private Integer totalSeats;
    private Integer bookedSeats;
    private Integer availableSeats;
    private Boolean isActive;
    private Boolean canBook;
}
