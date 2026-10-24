package com.nitditto.service;

import com.nitditto.enums.SeatAvailabilityStatus;
import com.nitditto.model.SeatInstance;
import java.time.Instant;
import java.util.UUID;

/** Quy tắc giữ chỗ dùng chung cho đặt/giữ ghế và cho phần hiển thị sơ đồ ghế. */
public final class SeatHoldPolicy {

  private SeatHoldPolicy() {
  }

  /** Còn đang bị giữ (heldUntil ở tương lai)? Hết hạn thì coi như không còn giữ. */
  public static boolean isHoldActive(final SeatInstance seat, final Instant now) {
    return seat.getHeldUntil() != null && seat.getHeldUntil().isAfter(now);
  }

  /** Đang bị một NGƯỜI KHÁC giữ chỗ (requester null = khách vãng lai, mọi hold đều là của người khác). */
  public static boolean isHeldByOther(final SeatInstance seat, final UUID requesterId, final Instant now) {
    return isHoldActive(seat, now) && (requesterId == null || !requesterId.equals(seat.getHeldBy()));
  }

  /** Ghế chưa bán và không bị người khác giữ → {@code userId} có thể giữ/đặt. */
  public static boolean isOpenFor(final SeatInstance seat, final UUID userId, final Instant now) {
    return seat.getStatus() == SeatAvailabilityStatus.AVAILABLE
        && !seat.isBooked()
        && seat.isAvailable()
        && !isHeldByOther(seat, userId, now);
  }
}
