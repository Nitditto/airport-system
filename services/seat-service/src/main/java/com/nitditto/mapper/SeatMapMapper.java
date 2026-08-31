package com.nitditto.mapper;

import com.nitditto.model.CabinClass;
import com.nitditto.model.SeatMap;
import com.nitditto.payload.request.SeatMapRequest;
import com.nitditto.payload.response.SeatMapResponse;

public class SeatMapMapper {

    public static SeatMap toEntity(SeatMapRequest seatMapRequest, CabinClass cabinClass) {
        return SeatMap.builder()
                .name(seatMapRequest.getName())
                .totalRows(seatMapRequest.getTotalRows())
                .leftSeatsPerRow(seatMapRequest.getLeftSeatsPerRow())
                .rightSeatsPerRow(seatMapRequest.getRightSeatsPerRow())
                .cabinClassId(cabinClass != null ? cabinClass.getId() : null)
                .build();
    }

    public static void updateEntity(SeatMapRequest seatMapRequest, SeatMap seatMap) {
        seatMap.setName(seatMapRequest.getName());
        seatMap.setTotalRows(seatMapRequest.getTotalRows());
        seatMap.setLeftSeatsPerRow(seatMapRequest.getLeftSeatsPerRow());
        seatMap.setRightSeatsPerRow(seatMapRequest.getRightSeatsPerRow());
    }

    /**
     * {@code cabinClass} do service batch-load sẵn rồi truyền vào (không traverse quan hệ,
     * vì SeatMap chỉ giữ UUID thô theo rule "NO JPA Relationship" của project).
     * Truyền {@code null} nếu caller không cần/không load cabin class — khi đó
     * {@code cabinClassName}/{@code cabinClassCode} sẽ null nhưng {@code cabinClassId} vẫn có.
     * <p>
     * {@code seats}/{@code availableSeats}/{@code occupiedSeats}/{@code windowSeats}... cố ý
     * để null: đó là dữ liệu tổng hợp từ bảng seats, muốn có phải thêm aggregate query ở tầng
     * service rồi truyền vào (KHÔNG cho mapper tự query). {@code totalSeats} thì suy ra được
     * ngay từ layout nên tính luôn tại đây.
     */
    public static SeatMapResponse toResponse(final SeatMap seatMap, final CabinClass cabinClass) {
        if (seatMap == null) return null;

        return SeatMapResponse.builder()
                .id(seatMap.getId())
                .name(seatMap.getName())
                .totalRows(seatMap.getTotalRows())
                .leftSeatsPerRow(seatMap.getLeftSeatsPerRow())
                .rightSeatsPerRow(seatMap.getRightSeatsPerRow())
                .totalSeats(seatMap.getTotalRows() * (seatMap.getLeftSeatsPerRow() + seatMap.getRightSeatsPerRow()))
                .airlineId(seatMap.getAirlineId())
                .cabinClassId(seatMap.getCabinClassId())
                .cabinClassName(cabinClass != null ? cabinClass.getName().toString() : null)
                .cabinClassCode(cabinClass != null ? cabinClass.getCode() : null)
                .build();
    }
}
