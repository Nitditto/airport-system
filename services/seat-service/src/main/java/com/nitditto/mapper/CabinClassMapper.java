package com.nitditto.mapper;

import com.nitditto.enums.CabinClassType;
import com.nitditto.model.CabinClass;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.CabinClassRequest;
import com.nitditto.payload.response.CabinClassResponse;

public class CabinClassMapper {

    public static CabinClass toEntity(final CabinClassRequest request) {
        if (request == null)
            return null;

        return CabinClass.builder()
                .name(CabinClassType.valueOf(request.getName()))
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .aircraftId(request.getAircraftId())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .isBookable(request.getIsBookable() != null ? request.getIsBookable() : true)
                .typicalSeatPitch(request.getTypicalSeatPitch())
                .typicalSeatWidth(request.getTypicalSeatWidth())
                .seatType(request.getSeatType())
                .build();
    }

    public static CabinClassResponse toResponse(final CabinClass cabinClass, SeatMap seatMap) {
        if (cabinClass == null)
            return null;

        return CabinClassResponse.builder()
                .id(cabinClass.getId())
                .name(cabinClass.getName().name())
                .code(cabinClass.getCode())
                .description(cabinClass.getDescription())
                .aircraftId(cabinClass.getAircraftId())
                .seatMap(seatMap != null ? SeatMapMapper.toSimpleResponse(seatMap) : null)
                .displayOrder(cabinClass.getDisplayOrder())
                .isActive(cabinClass.getIsActive())
                .isBookable(cabinClass.getIsBookable())
                .typicalSeatPitch(cabinClass.getTypicalSeatPitch())
                .typicalSeatWidth(cabinClass.getTypicalSeatWidth())
                .seatType(cabinClass.getSeatType())
                .createdAt(cabinClass.getCreatedAt())
                .updatedAt(cabinClass.getUpdatedAt())
                .build();
    }

    public static void updateEntity(final CabinClassRequest request, final CabinClass cabinClass) {
        if (request == null || cabinClass == null)
            return;
        if (request.getName() != null)
            cabinClass.setName(CabinClassType.valueOf(request.getName()));
        if (request.getCode() != null)
            cabinClass.setCode(request.getCode().toUpperCase());
        if (request.getDescription() != null)
            cabinClass.setDescription(request.getDescription());
        if (request.getDisplayOrder() != null)
            cabinClass.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null)
            cabinClass.setIsActive(request.getIsActive());
        if (request.getIsBookable() != null)
            cabinClass.setIsBookable(request.getIsBookable());
        if (request.getTypicalSeatPitch() != null)
            cabinClass.setTypicalSeatPitch(request.getTypicalSeatPitch());
        if (request.getTypicalSeatWidth() != null)
            cabinClass.setTypicalSeatWidth(request.getTypicalSeatWidth());
        if (request.getSeatType() != null)
            cabinClass.setSeatType(request.getSeatType());

    }
}
