package com.nitditto.service;

import com.nitditto.service.integration.BookingTargetResolver.BookingTarget;
import java.util.List;
import java.util.UUID;

/** Giao tiếp giữ/đặt ghế với seat-service, dịch lỗi Feign thành BusinessException có thông điệp rõ ràng. */
public interface SeatReservationService {

  /**
   * Giữ chỗ tạm thời, chỉ cho ghế thuộc đúng chuyến và hạng ghế của fare trong {@code target}; ném BusinessException
   * nếu có ghế đã bị người khác giữ/đặt hoặc nằm ngoài phạm vi đó.
   */
  void hold(UUID userId, List<UUID> seatInstanceIds, BookingTarget target);

  /** Nhả hold của chính người dùng, nuốt lỗi (chỉ ghi log) vì chỉ là dọn dẹp. */
  void releaseQuietly(UUID userId, List<UUID> seatInstanceIds);

  void confirmBooked(UUID userId, List<UUID> seatInstanceIds);

  /** Trả ghế đã bán về trống (huỷ booking đã xác nhận); nuốt lỗi như releaseQuietly. */
  void freeQuietly(List<UUID> seatInstanceIds);
}
