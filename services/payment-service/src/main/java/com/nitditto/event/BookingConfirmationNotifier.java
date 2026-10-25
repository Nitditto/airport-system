package com.nitditto.event;

import com.nitditto.client.BookingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Báo booking-service xác nhận đơn sau khi thanh toán thành công.
 *
 * <p>Phải chạy AFTER_COMMIT: booking-service gọi ngược payment-service để kiểm tra lại payment SUCCESS, nếu gọi trước
 * khi commit nó sẽ thấy payment còn PENDING và từ chối. Lỗi chỉ ghi log (không làm hỏng việc ghi nhận thanh toán):
 * booking-service tự đối soát lại khi người dùng mở danh sách vé nên đơn không bị kẹt PENDING vĩnh viễn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingConfirmationNotifier {

  private final BookingClient bookingClient;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPaymentSucceeded(final PaymentSucceededEvent event) {
    try {
      this.bookingClient.confirmPayment(event.bookingId());
      log.info("Booking {} confirmed after payment {}", event.bookingId(), event.paymentId());
    } catch (final RuntimeException ex) {
      log.error("Payment {} succeeded but booking {} could not be confirmed yet (will be reconciled on next read): {}",
          event.paymentId(), event.bookingId(), ex.getMessage());
    }
  }
}
