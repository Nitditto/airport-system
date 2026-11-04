package com.nitditto.service;

import com.nitditto.payload.response.SeatHoldResponse;
import java.util.List;
import java.util.UUID;

public interface SeatHoldService {

  /** Giữ chỗ tạm thời các ghế cho người dùng (TTL cấu hình); bỏ giữ các ghế khác người đó đang giữ trên cùng chuyến. */
  SeatHoldResponse hold(UUID userId, List<UUID> seatInstanceIds);

  /** Nhả các ghế mà chính người dùng đang giữ (không đụng ghế của người khác). Idempotent. */
  void release(UUID userId, List<UUID> seatInstanceIds);

  /** Chuyển ghế sang đã bán sau khi thanh toán (nội bộ, booking-service gọi). */
  void confirmBooked(UUID userId, List<UUID> seatInstanceIds);

  /** Trả ghế đã bán về trống khi huỷ/hết hạn booking (nội bộ). */
  void free(List<UUID> seatInstanceIds);
}
