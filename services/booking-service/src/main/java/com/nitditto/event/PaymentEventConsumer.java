package com.nitditto.event;

import com.nitditto.payload.event.PaymentTransactionFailedEvent;
import com.nitditto.payload.event.PaymentTransactionSucceededEvent;
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

  @KafkaListener(topics = "${kafka.topics.payment-transaction-succeeded}", groupId = "${spring.application.name}")
  public void handlePaymentSucceeded(final PaymentTransactionSucceededEvent event) {
    log.info("Received PaymentTransactionSucceededEvent eventId={} bookingId={} paymentId={}", event.eventId(),
        event.bookingId(), event.paymentId());
    this.bookingConfirmationService.confirmPayment(event.bookingId());
    log.info("Booking {} confirmed via Kafka event {}", event.bookingId(), event.eventId());
  }

  @KafkaListener(topics = "${kafka.topics.payment-transaction-failed}", groupId = "${spring.application.name}")
  public void handlePaymentFailed(final PaymentTransactionFailedEvent event) {
    // Không huỷ booking: người dùng còn được thanh toán lại trong thời hạn; booking hết hạn sẽ do expireIfStale xử lý.
    log.warn("Received PaymentTransactionFailedEvent eventId={} bookingId={} paymentId={} reason={}", event.eventId(),
        event.bookingId(), event.paymentId(), event.failureReason());
  }
}
