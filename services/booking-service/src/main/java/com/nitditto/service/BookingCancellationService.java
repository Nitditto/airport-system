package com.nitditto.service;

import com.nitditto.model.Booking;
import java.util.UUID;

/**
 * Phần GHI DB của việc huỷ booking, tách khỏi {@link BookingService} để transaction chỉ bao quanh việc ghi, không bao
 * quanh các lời gọi HTTP (hoàn tiền, nhả ghế). Gọi qua bean này (qua proxy), không gọi nội bộ.
 */
public interface BookingCancellationService {

  /** Đặt booking CANCELLED và huỷ vé trong một transaction riêng; trả về booking đã commit. */
  Booking markCancelled(UUID bookingId);
}
