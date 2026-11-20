package com.nitditto.service;

import com.nitditto.model.Booking;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thời hạn thanh toán của một booking ({@code booking.payment-window-minutes}): MỘT nơi duy nhất định nghĩa, dùng cả để hết
 * hạn booking lẫn để đặt hạn cho link thanh toán của cổng, nên hai thứ này không bao giờ lệch nhau.
 */
@Component
public class PaymentWindow {

  private final Duration window;

  public PaymentWindow(@Value("${booking.payment-window-minutes:15}") final long minutes) {
    this.window = Duration.ofMinutes(minutes);
  }

  public Duration duration() {
    return this.window;
  }

  /** Booking hết hạn thanh toán lúc nào; null nếu booking chưa có thời điểm tạo. */
  public Instant expiresAt(final Booking booking) {
    return booking.getCreatedAt() == null ? null : booking.getCreatedAt().plus(this.window);
  }
}
