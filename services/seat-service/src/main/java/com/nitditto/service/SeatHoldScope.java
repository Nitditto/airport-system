package com.nitditto.service;

import java.util.UUID;

/**
 * Phạm vi ghế được phép giữ trong một lần hold. Trường null = không ràng buộc (khách giữ ghế trực tiếp qua gateway
 * chưa gửi); booking-service luôn gửi cả hai để ghế khớp chuyến và hạng của fare đã chọn.
 */
public record SeatHoldScope(UUID flightInstanceId, UUID cabinClassId) {

  public static final SeatHoldScope ANY = new SeatHoldScope(null, null);
}
