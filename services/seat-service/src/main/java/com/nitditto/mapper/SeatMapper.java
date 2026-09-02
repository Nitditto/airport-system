package com.nitditto.mapper;

import com.nitditto.model.CabinClass;
import com.nitditto.model.Seat;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.SeatRequest;
import com.nitditto.payload.response.SeatResponse;

public class SeatMapper {

    /**
     * {@code seatMap}/{@code cabinClass} do service batch-load sẵn rồi truyền vào (không
     * traverse quan hệ, vì Seat chỉ giữ seatMapId/cabinClassId dạng UUID thô theo rule
     * "NO JPA Relationship" của project). Truyền {@code null} nếu caller không cần/không
     * load — khi đó {@code seatMapName}/{@code cabinClassName} sẽ null.
     */
    public static SeatResponse toResponse(final Seat seat, final SeatMap seatMap, final CabinClass cabinClass) {
        if (seat == null) return null;

        return SeatResponse.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .seatRow(seat.getSeatRow())
                .columnLetter(seat.getColumnLetter())
                .seatType(seat.getSeatType())
                .isAvailable(seat.getIsAvailable())
                .isBooked(seat.getIsBooked())
                .isEmergencyExit(seat.getIsEmergencyExit())
                .isActive(seat.getIsActive())
                .basePrice(seat.getBasePrice())
                .premiumSuperCharge(seat.getPremiumSuperCharge())
                .totalPrice(seat.getTotalPrice())
                .hasExtraLegroom(seat.getHasExtraLegroom())
                .hasPowerOutlet(seat.getHasPowerOutlet())
                .hasTvScreen(seat.getHasTvScreen())
                .hasExtraWidth(seat.getHasExtraWidth())
                .seatPitch(seat.getSeatPitch())
                .seatWidth(seat.getSeatWidth())
                .reclineAngle(seat.getReclineAngle())
                .seatMapId(seat.getSeatMapId())
                .seatMapName(seatMap != null ? seatMap.getName() : null)
                .cabinClassId(seat.getCabinClassId())
                .cabinClassName(cabinClass != null ? cabinClass.getName().toString() : null)
                .createdAt(seat.getCreatedAt())
                .updatedAt(seat.getUpdatedAt())
                .isPremiumSeat(seat.isPremiumSeat())
                .isBookable(seat.isBookable())
                .fullPosition(seat.getFullPosition())
                .seatCharacteristics(seat.getSeatCharacteristics())
                .build();
    }

    /**
     * Chỉ cập nhật thuộc tính mô tả/nghiệp vụ của ghế (giá, trạng thái, tiện ích...).
     * KHÔNG đụng seatMapId/cabinClassId/seatNumber/seatRow/columnLetter — đây là field
     * cấu trúc, do {@code SeatServiceImpl.generateSeats()} sinh ra 1 lần theo layout của
     * SeatMap; cho phép sửa qua đây sẽ làm ghế "trôi" khỏi đúng sơ đồ đã sinh.
     */
    public static void updateEntity(final SeatRequest request, final Seat seat) {
        if (request == null || seat == null) return;
        if (request.getSeatType() != null) seat.setSeatType(request.getSeatType());
        if (request.getIsAvailable() != null) seat.setIsAvailable(request.getIsAvailable());
        if (request.getIsBooked() != null) seat.setIsBooked(request.getIsBooked());
        if (request.getIsEmergencyExit() != null) seat.setIsEmergencyExit(request.getIsEmergencyExit());
        if (request.getIsActive() != null) seat.setIsActive(request.getIsActive());
        if (request.getBasePrice() != null) seat.setBasePrice(request.getBasePrice());
        if (request.getPremiumSuperCharge() != null) seat.setPremiumSuperCharge(request.getPremiumSuperCharge());
        if (request.getHasExtraLegroom() != null) seat.setHasExtraLegroom(request.getHasExtraLegroom());
        if (request.getHasPowerOutlet() != null) seat.setHasPowerOutlet(request.getHasPowerOutlet());
        if (request.getHasTvScreen() != null) seat.setHasTvScreen(request.getHasTvScreen());
        if (request.getHasExtraWidth() != null) seat.setHasExtraWidth(request.getHasExtraWidth());
        if (request.getSeatPitch() != null) seat.setSeatPitch(request.getSeatPitch());
        if (request.getSeatWidth() != null) seat.setSeatWidth(request.getSeatWidth());
    }
}
