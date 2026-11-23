package com.nitditto.service;

import com.nitditto.model.Booking;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thời hạn thanh toán của một booking ({@code booking.payment-window-minutes}): MỘT nơi duy nhất định nghĩa, dùng cả để hết
 * hạn booking lẫn để đặt hạn cho link thanh toán của cổng, nên hai thứ này không bao giờ lệch nhau.
 */
@Component
public class PaymentWindow {

  private final Duration window;
  private final Clock clock;

  @Autowired
  public PaymentWindow(@Value("${booking.payment-window-minutes:15}") final long minutes, final Clock clock) {
    this.window = Duration.ofMinutes(minutes);
    this.clock = clock;
  }

  public PaymentWindow(final long minutes) {
    this(minutes, Clock.systemUTC());
  }

  /**
   * Đã quá hạn thanh toán chưa, tính tại chỗ (không khoá hàng, không gọi payment-service). Không có thời điểm tạo
   * thì coi là đã quá hạn để bước kiểm tra kỹ (có khoá) quyết định.
   */
  public boolean hasExpired(final Booking booking) {
    final Instant deadline = this.expiresAt(booking);
    return deadline == null || !this.clock.instant().isBefore(deadline);
  }

  public Duration duration() {
    return this.window;
  }

  /** Booking hết hạn thanh toán lúc nào; null nếu booking chưa có thời điểm tạo. */
  public Instant expiresAt(final Booking booking) {
    return booking.getCreatedAt() == null ? null : booking.getCreatedAt().plus(this.window);
  }
}
