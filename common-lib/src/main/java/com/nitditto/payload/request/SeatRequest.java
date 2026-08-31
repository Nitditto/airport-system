package com.nitditto.payload.request;

import lombok.Builder;
import lombok.Data;

import com.nitditto.enums.SeatType;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Dùng cho luồng cập nhật thông tin mô tả của 1 ghế (SeatServiceImpl.updateSeats).
 * Ghế được tạo hàng loạt theo layout của SeatMap (SeatServiceImpl.generateSeats), không
 * qua DTO này, nên seatNumber/seatRow/seatType/seatMapId/cabinClassId là field cấu trúc,
 * cố tình KHÔNG bắt buộc và bị SeatMapper.updateEntity() bỏ qua khi cập nhật.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatRequest {

    private SeatType seatType;

    private Boolean isAvailable;
    private Boolean isBooked;
    private Boolean isEmergencyExit;
    private Boolean isActive;

    private Double basePrice;
    private Double premiumSuperCharge;

    private Boolean hasExtraLegroom;

    private Boolean hasPowerOutlet;
    private Boolean hasTvScreen;

    private Boolean hasExtraWidth;

    private Integer seatPitch;
    private Integer seatWidth;
}
