package com.nitditto.service;

import com.nitditto.exception.ForbiddenException;
import com.nitditto.model.Booking;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Ai được xem một booking: chính chủ (userId của booking), chủ hãng bay sở hữu booking đó (cùng quyền họ đã có ở danh
 * sách booking theo hãng), hoặc ROLE_SYSTEM_ADMIN (hỗ trợ/kiểm soát, chỉ ĐỌC). Người khác bị từ chối: id booking không
 * phải bí mật nên không đủ để truy cập.
 *
 * <p>Quyền sở hữu đối chiếu X-User-Id, vai trò admin lấy từ X-User-Roles; cả hai do gateway ghi đè từ JWT (CLAUDE.md §10).
 * {@code requesterAirlineId} được gọi lười: chủ booking và admin không tốn lời gọi sang airline-core.
 */
public final class BookingReadAccess {

  private static final String ADMIN_ROLE = "ROLE_SYSTEM_ADMIN";

  private BookingReadAccess() {
  }

  /** {@code rolesHeader}: chuỗi quyền phân tách bằng dấu phẩy (claim authorities của JWT); null/rỗng = không có quyền. */
  public static boolean hasAdminRole(final String rolesHeader) {
    if (rolesHeader == null || rolesHeader.isBlank()) {
      return false;
    }
    for (final String role : rolesHeader.split(",")) {
      if (ADMIN_ROLE.equals(role.trim())) {
        return true;
      }
    }
    return false;
  }

  public static void require(final Booking booking, final UUID requesterUserId, final boolean requesterIsAdmin,
      final Supplier<UUID> requesterAirlineId) {
    if (requesterIsAdmin || requesterUserId.equals(booking.getUserId())) {
      return;
    }
    final UUID bookingAirlineId = booking.getAirlineId();
    if (bookingAirlineId != null && bookingAirlineId.equals(requesterAirlineId.get())) {
      return;
    }
    throw new ForbiddenException("You are not allowed to view this booking");
  }
}
