package com.nitditto.service;

import com.nitditto.exception.ForbiddenException;
import com.nitditto.model.Booking;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Ai được xem một booking: chính chủ (userId của booking) hoặc chủ hãng bay sở hữu booking đó (cùng quyền họ đã có ở
 * danh sách booking theo hãng). Người khác bị từ chối: id booking không phải bí mật nên không đủ để truy cập.
 *
 * <p>Vai trò (role) kiểm tra ở gateway; đây là kiểm tra QUYỀN SỞ HỮU theo CLAUDE.md §10 (đối chiếu X-User-Id).
 * {@code requesterAirlineId} được gọi lười: chủ booking đọc booking của mình không tốn lời gọi sang airline-core.
 */
public final class BookingReadAccess {

  private BookingReadAccess() {
  }

  public static void require(final Booking booking, final UUID requesterUserId,
      final Supplier<UUID> requesterAirlineId) {
    if (requesterUserId.equals(booking.getUserId())) {
      return;
    }
    final UUID bookingAirlineId = booking.getAirlineId();
    if (bookingAirlineId != null && bookingAirlineId.equals(requesterAirlineId.get())) {
      return;
    }
    throw new ForbiddenException("You are not allowed to view this booking");
  }
}
