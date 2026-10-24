package com.nitditto.service;

import com.nitditto.enums.BookingStatus;
import java.util.UUID;

public interface BookingConfirmationService {

  /**
   * Xác nhận booking sau khi thanh toán thành công. Idempotent. Tự kiểm tra lại với payment-service rằng có
   * thanh toán SUCCESS (không tin người gọi), rồi mới chốt ghế, phát hành vé và chuyển trạng thái CONFIRMED.
   */
  BookingStatus confirmPayment(UUID bookingId);

  /** Booking PENDING quá hạn thanh toán và không có thanh toán SUCCESS thì chuyển EXPIRED; trả trạng thái hiện tại. */
  BookingStatus expireIfStale(UUID bookingId);
}
