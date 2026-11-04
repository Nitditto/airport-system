package com.nitditto.event;

import com.nitditto.service.BookingConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Xác nhận booking khi payment thành công. payment-service (PaymentOutcomePublisher) gửi event này rồi gọi thêm HTTP
 * confirm ngay sau commit; Kafka là đường bền vững (retry được khi booking-service down).
 *
 * <p>booking-service là bên DUY NHẤT chốt/nhả ghế (qua SeatReservationService). confirmPayment idempotent: khoá dòng
 * booking và kiểm tra CONFIRMED trước mọi lời gọi ra ngoài, nên nhận cả HTTP lẫn Kafka là an toàn.
 *
 * <p>Không bắt exception ở đây: để DefaultErrorHandler (KafkaConsumerConfig) retry lỗi tạm thời.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

  private final BookingConfirmationService bookingConfirmationService;

  @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "${spring.kafka.consumer.group-id:booking-service-group}")
  public void handlePaymentCompleted(final PaymentCompletedEvent event) {
    log.info("Received PaymentCompletedEvent for bookingId={}, paymentId={}", event.getBookingId(), event.getPaymentId());
    this.bookingConfirmationService.confirmPayment(event.getBookingId());
    log.info("Booking {} confirmed via Kafka event", event.getBookingId());
  }

  @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "${spring.kafka.consumer.group-id:booking-service-group}")
  public void handlePaymentFailed(final PaymentFailedEvent event) {
    // Không huỷ booking: người dùng còn được thanh toán lại trong thời hạn; booking hết hạn sẽ do expireIfStale xử lý.
    log.warn("Received PaymentFailedEvent for bookingId={}, paymentId={}, reason={}",
        event.getBookingId(), event.getPaymentId(), event.getFailureReason());
  }
}
