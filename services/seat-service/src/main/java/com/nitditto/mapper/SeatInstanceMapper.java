package com.nitditto.mapper;

import com.nitditto.enums.SeatAvailabilityStatus;
import com.nitditto.model.CabinClass;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatInstance;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.response.SeatInstanceResponse;

public class SeatInstanceMapper {

    /**
     * {@code seat}/{@code seatMap}/{@code cabinClass} do service batch-load sẵn rồi truyền
     * vào (không traverse quan hệ, vì SeatInstance chỉ giữ seatId dạng UUID thô theo rule
     * "NO JPA Relationship" của project). {@code seat} có thể null nếu caller không cần
     * nested {@code seat: SeatResponse} — khi đó field đó sẽ null.
     */
    public record SeatInstanceContext(SeatInstance seatInstance, Seat seat, SeatMap seatMap, CabinClass cabinClass) {
    }

    public static SeatInstanceResponse toResponse(final SeatInstanceContext context) {
        if (context == null || context.seatInstance() == null) return null;

        final SeatInstance seatInstance = context.seatInstance();

        return SeatInstanceResponse.builder()
                .id(seatInstance.getId())
                .flightId(seatInstance.getFlightId())
                .flightInstanceId(seatInstance.getFlightInstanceId())
                .flightInstanceCabinId(seatInstance.getFlightInstanceCabinId())
                .seatId(seatInstance.getSeatId())
                .seat(SeatMapper.toResponse(context.seat(), context.seatMap(), context.cabinClass()))
                .status(seatInstance.getStatus())
                .isBooked(seatInstance.isBooked())
                .isAvailable(seatInstance.isAvailable())
                .isOccupied(seatInstance.getStatus() == SeatAvailabilityStatus.OCCUPIED)
                .fare(seatInstance.getFare())
                .premiumSuperCharge(seatInstance.getPremiumSuperCharge())
                .version(seatInstance.getVersion())
                .createdAt(seatInstance.getCreatedAt())
                .updatedAt(seatInstance.getUpdatedAt())
                .build();
    }
}
